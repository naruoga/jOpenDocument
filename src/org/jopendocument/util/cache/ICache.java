/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2013 jOpenDocument, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU
 * General Public License Version 3 only ("GPL").  
 * You may not use this file except in compliance with the License. 
 * You can obtain a copy of the License at http://www.gnu.org/licenses/gpl-3.0.html
 * See the License for the specific language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 * 
 */

package org.jopendocument.util.cache;

import org.jopendocument.util.Log;
import org.jopendocument.util.cache.CacheItem.RemovalType;
import org.jopendocument.util.cache.CacheResult.State;
import org.jopendocument.util.cc.IClosure;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.jcip.annotations.GuardedBy;

/**
 * To keep results computed from some data. The results will be automatically invalidated after some
 * period of time or when the data is modified.
 * 
 * @author Sylvain CUAZ
 * @param <K> key type, eg String.
 * @param <V> value type, eg List of SQLRow.
 * @param <D> source data type, eg SQLTable.
 */
public class ICache<K, V, D> {

    private static final Level LEVEL = Level.FINEST;

    public static final String ITEMS_CHANGED = "itemsChanged";
    public static final String ITEM_ADDED = "itemAdded";
    public static final String ITEM_REMOVED = "itemRemoved";

    private final ICacheSupport<D> supp;
    // linked to fifo, ATTN the values in this map can be invalid since clear() is called without
    // the lock on CacheValue
    @GuardedBy("this")
    private final LinkedHashMap<K, CacheItem<K, V, D>> cache;
    @GuardedBy("this")
    private final Map<K, CacheItem<K, V, D>> running;
    private final int delay;
    private final int size;
    private final String name;

    private ICache<K, V, D> parent;

    private final PropertyChangeSupport propSupp = new PropertyChangeSupport(this);

    public ICache() {
        this(60);
    }

    public ICache(int delay) {
        this(delay, -1);
    }

    public ICache(int delay, int size) {
        this(delay, size, null);
    }

    /**
     * Creates a cache with the given parameters.
     * 
     * @param delay the delay in seconds before a key is cleared.
     * @param size the maximum size of the cache, negative means no limit.
     * @param name name of this cache and associated thread.
     * @throws IllegalArgumentException if size is 0.
     */
    public ICache(int delay, int size, String name) {
        this(null, delay, size, name);
    }

    public ICache(final ICacheSupport<D> supp, int delay, int size, String name) {
        this.supp = supp == null ? createSupp(getCacheSuppName(name)) : supp;
        this.running = new HashMap<K, CacheItem<K, V, D>>();
        this.delay = delay;
        if (size == 0)
            throw new IllegalArgumentException("0 size");
        this.size = size;
        this.cache = new LinkedHashMap<K, CacheItem<K, V, D>>(size < 0 ? 64 : size);
        this.name = name;

        this.parent = null;
    }

    protected ICacheSupport<D> createSupp(final String name) {
        return new ICacheSupport<D>(name);
    }

    protected String getCacheSuppName(final String cacheName) {
        return cacheName;
    }

    public final ICacheSupport<D> getSupp() {
        return this.supp;
    }

    public final int getMaximumSize() {
        return this.size;
    }

    public final String getName() {
        return this.name;
    }

    /**
     * Allow to continue the search for a key in another instance.
     * 
     * @param parent the cache to search when a key isn't found in this.
     */
    public final synchronized void setParent(final ICache<K, V, D> parent) {
        ICache<K, V, D> current = parent;
        while (current != null) {
            if (current == this)
                throw new IllegalArgumentException("Cycle detected, cannot set parent to " + parent);
            current = current.getParent();
        }
        this.parent = parent;
    }

    public final synchronized ICache<K, V, D> getParent() {
        return this.parent;
    }

    /**
     * If <code>sel</code> is in cache returns its value, else if key is running block until the key
     * is put (or the current thread is interrupted). Then if a {@link #setParent(ICache) parent}
     * has been set, use it. Otherwise the key is not in cache so return a CacheResult of state
     * {@link State#NOT_IN_CACHE}.
     * 
     * @param sel the key we're getting the value for.
     * @return a CacheResult with the appropriate state.
     */
    public final CacheResult<V> get(K sel) {
        return this.get(sel, true);
    }

    private final CacheResult<V> get(K sel, final boolean checkRunning) {
        ICache<K, V, D> parent = null;
        synchronized (this) {
            final CacheResult<V> localRes = this.cache.containsKey(sel) ? this.cache.get(sel).getResult() : CacheResult.<V> getNotInCache();
            if (localRes.getState() == State.VALID) {
                log("IN cache", sel);
                return localRes;
            } else if (checkRunning && isRunning(sel)) {
                log("RUNNING", sel);
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    // return sinon thread ne peut sortir que lorsque sel sera fini
                    return CacheResult.getInterrupted();
                }
                return this.get(sel);
            } else if (this.parent != null) {
                log("CALLING parent", sel);
                parent = this.parent;
            } else {
                log("NOT in cache", sel);
                return CacheResult.getNotInCache();
            }
        }
        // don't call our parent with our lock
        return parent.get(sel, false);
    }

    /**
     * Tell this cache that we're in process of getting the value for key, so if someone else ask
     * have them wait. ATTN after calling this method you MUST call put() or removeRunning(),
     * otherwise get() will always block for <code>key</code>.
     * 
     * @param val the value that will receive the result.
     * @return <code>true</code> if the value was added, <code>false</code> if the key was already
     *         running.
     * @see #put(Object, Object, Set)
     * @see #removeRunning(Object)
     */
    private final synchronized boolean addRunning(final CacheItem<K, V, D> val) {
        if (!this.isRunning(val.getKey())) {
            // ATTN this can invalidate val
            val.addToWatchers();
            if (val.getRemovalType() == null) {
                this.running.put(val.getKey(), val);
                return true;
            }
        }
        return false;
    }

    // return null if the item wasn't added to this
    final CacheItem<K, V, D> getRunningValFromRes(final CacheResult<V> cacheRes) {
        if (cacheRes.getState() != CacheResult.State.NOT_IN_CACHE)
            throw new IllegalArgumentException("Wrong state : " + cacheRes.getState());
        if (cacheRes.getVal() == null) {
            // happens when check() is called and ICacheSupport is dead, i.e. this.running was not
            // modified and CacheResult.getNotInCache() was returned
            assert cacheRes == CacheResult.getNotInCache();
        } else {
            if (cacheRes.getVal().getCache() != this)
                throw new IllegalArgumentException("Not running in this cache");
            assert cacheRes.getVal().getState() == CacheItem.State.RUNNING || cacheRes.getVal().getState() == CacheItem.State.INVALID;
        }
        @SuppressWarnings("unchecked")
        final CacheItem<K, V, D> res = (CacheItem<K, V, D>) cacheRes.getVal();
        return res;
    }

    public final synchronized void removeRunning(final CacheResult<V> res) {
        removeRunning(getRunningValFromRes(res));
    }

    private final synchronized boolean removeRunning(final CacheItem<K, V, D> val) {
        if (val == null)
            return false;
        final K key = val.getKey();
        final boolean removed;
        if (this.running.get(key) == val) {
            this.removeRunning(key);
            removed = true;
        } else {
            // either val wasn't created in this cache or another value was already put in this
            // cache
            removed = val.setRemovalType(RemovalType.EXPLICIT);
        }
        assert val.getRemovalType() != null;
        return removed;
    }

    private final synchronized void removeRunning(K key) {
        final CacheItem<K, V, D> removed = this.running.remove(key);
        if (removed != null) {
            // if the removed value isn't in us (this happens if put() is called without passing the
            // value returned by check()), kill it so that it stops listening to its data
            if (this.cache.get(key) != removed)
                removed.setRemovalType(RemovalType.EXPLICIT);
            this.notifyAll();
        }
    }

    public final synchronized boolean isRunning(K sel) {
        return this.running.containsKey(sel);
    }

    public final synchronized Set<K> getRunning() {
        return Collections.unmodifiableSet(new HashSet<K>(this.running.keySet()));
    }

    /**
     * Check if key is in cache, in that case returns the value otherwise adds key to running and
     * returns <code>NOT_IN_CACHE</code>.
     * 
     * @param key the key to be checked.
     * @return the associated value, never <code>null</code>.
     * @see #addRunning(Object)
     * @see #removeRunning(CacheResult)
     * @see #put(CacheResult, Object, long)
     */
    public final CacheResult<V> check(K key) {
        return this.check(key, Collections.<D> emptySet());
    }

    public final CacheResult<V> check(K key, final Set<? extends D> data) {
        return this.check(key, true, true, data);
    }

    public final CacheResult<V> check(K key, final boolean readCache, final boolean willWriteToCache, final Set<? extends D> data) {
        return this.check(key, readCache, willWriteToCache, data, this.delay * 1000);
    }

    public final synchronized CacheResult<V> check(K key, final boolean readCache, final boolean willWriteToCache, final Set<? extends D> data, final long timeout) {
        final CacheResult<V> l = readCache ? this.get(key) : CacheResult.<V> getNotInCache();
        if (willWriteToCache && l.getState() == State.NOT_IN_CACHE) {
            final CacheItem<K, V, D> val = new CacheItem<K, V, D>(this, key, data);
            if (this.addRunning(val)) {
                val.addTimeout(timeout, TimeUnit.MILLISECONDS);
                return new CacheResult<V>(val);
            } else {
                // val was never referenced so it will be garbage collected
                assert !val.getState().isActive() : "active value : " + val;
            }
        }
        return l;
    }

    /**
     * Put a result which doesn't depend on variable data in this cache.
     * 
     * @param sel the key.
     * @param res the result associated with <code>sel</code>.
     * @return the item that was created.
     */
    public final CacheItem<K, V, D> put(K sel, V res) {
        return this.put(sel, res, Collections.<D> emptySet());
    }

    /**
     * Put a result in this cache.
     * 
     * @param sel the key.
     * @param res the result associated with <code>sel</code>.
     * @param data the data from which <code>res</code> is computed.
     * @return the item that was created.
     */
    public final CacheItem<K, V, D> put(K sel, V res, Set<? extends D> data) {
        return this.put(sel, res, data, this.delay * 1000);
    }

    public final CacheItem<K, V, D> put(K sel, V res, Set<? extends D> data, final long timeoutDelay) {
        return this.put(sel, true, res, data, timeoutDelay);
    }

    private final CacheItem<K, V, D> put(K key, final boolean allowReplace, V res, Set<? extends D> data, final long timeoutDelay) {
        final CacheItem<K, V, D> item = new CacheItem<K, V, D>(this, key, res, data);
        item.addTimeout(timeoutDelay, TimeUnit.MILLISECONDS);
        item.addToWatchers();
        return put(item, allowReplace);
    }

    /**
     * Assign a value to a {@link CacheItem.State#RUNNING} item.
     * 
     * @param cacheRes an instance obtained from <code>check()</code>.
     * @param val the value to store.
     * @return the item that was added, <code>null</code> if none was added.
     * @see #check(Object, boolean, boolean, Set)
     */
    public final CacheItem<K, V, D> put(CacheResult<V> cacheRes, V val) {
        final CacheItem<K, V, D> item = getRunningValFromRes(cacheRes);
        if (item == null)
            return null;
        item.setValue(val);
        return put(item, true);
    }

    private final CacheItem<K, V, D> put(final CacheItem<K, V, D> val, final boolean allowReplace) {
        final K sel = val.getKey();
        synchronized (this) {
            final CacheItem.State valState = val.getState();
            if (!valState.isActive())
                return null;
            else if (valState != CacheItem.State.VALID)
                throw new IllegalStateException("Non valid : " + val);
            final boolean replacing = this.cache.containsKey(sel) && this.cache.get(sel).getRemovalType() == null;
            if (!allowReplace && replacing)
                return null;

            if (!replacing && this.size > 0 && this.cache.size() == this.size)
                this.cache.values().iterator().next().setRemovalType(RemovalType.SIZE_LIMIT);
            final CacheItem<K, V, D> prev = this.cache.put(sel, val);
            if (replacing)
                prev.setRemovalType(RemovalType.DATA_CHANGE);
            assert this.size <= 0 || this.cache.size() <= this.size;
            this.removeRunning(sel);
        }
        this.propSupp.firePropertyChange(new Event<K, V, D>(this, ITEMS_CHANGED, null, null));
        this.propSupp.firePropertyChange(this.createItemEvent(ITEM_ADDED, null, val));
        return val;
    }

    /**
     * Get the remaining time before the passed key will be removed.
     * 
     * @param key the key.
     * @return the remaining milliseconds before the removal, negative if the passed key isn't in
     *         this.
     * @see #getRemovalTime(Object)
     */
    public final long getRemainingTime(K key) {
        final CacheItem<K, V, D> val;
        synchronized (this) {
            val = this.cache.get(key);
        }
        if (val == null)
            return -1;
        return val.getRemainingTimeoutDelay();
    }

    public final void putAll(final ICache<K, V, D> otherCache, final boolean allowReplace) {
        if (otherCache == this)
            return;
        if (otherCache.getSupp() != this.getSupp())
            Log.get().warning("Since both caches don't share watchers, some early events might not be notified to this cache");
        final List<CacheItem<K, V, D>> oItems = new ArrayList<CacheItem<K, V, D>>();
        synchronized (otherCache) {
            oItems.addAll(otherCache.cache.values());
        }
        for (final CacheItem<K, V, D> oItem : oItems) {
            final CacheItem<K, V, D> newItem = this.put(oItem.getKey(), allowReplace, oItem.getValue(), oItem.getData(), oItem.getRemainingTimeoutDelay());
            // if oItem was changed before newItem was created or see CacheWatcher.dataChanged() :
            // 1. if newItem was added to a watcher before the first synchronized block, it will be
            // notified
            // 2. if newItem was added between the synchronized blocks (during the first iteration)
            // it will be notified by the second iteration
            // 3. if newItem was added after the second synchronized block, oItem will already be
            // notified
            if (newItem != null && oItem.getRemovalType() == RemovalType.DATA_CHANGE) {
                newItem.setRemovalType(oItem.getRemovalType());
            }
        }
    }

    public final ICache<K, V, D> copy(final String name, final boolean copyItems) {
        final ICache<K, V, D> res = new ICache<K, V, D>(this.getSupp(), this.delay, this.getMaximumSize(), name);
        if (copyItems)
            res.putAll(this, false);
        return res;
    }

    public final synchronized void clear(K select) {
        log("clear", select);
        if (this.cache.containsKey(select)) {
            this.cache.get(select).setRemovalType(RemovalType.EXPLICIT);
        }
    }

    final boolean clear(final CacheItem<K, V, D> val) {
        if (val.getRemovalType() == null)
            throw new IllegalStateException("Not yet removed : " + val);
        final boolean removedFromRunning, toBeRemoved;
        synchronized (this) {
            log("clear", val);
            removedFromRunning = this.removeRunning(val);
            toBeRemoved = this.cache.get(val.getKey()) == val;
            if (toBeRemoved) {
                this.cache.remove(val.getKey());
            }
        }
        // NOTE these events are often fired with our monitor since this method is called with it
        if (removedFromRunning || toBeRemoved) {
            this.propSupp.firePropertyChange(new Event<K, V, D>(this, ITEMS_CHANGED, null, null));
            this.propSupp.firePropertyChange(this.createItemEvent(ITEM_REMOVED, val, null));
        }
        return toBeRemoved;
    }

    public final synchronized void clear() {
        for (final CacheItem<K, V, D> val : new ArrayList<CacheItem<K, V, D>>(this.cache.values())) {
            // We have our monitor so if val is still in us but setRemovalType() was already called,
            // then it means another thread is waiting on our monitor in clear(CacheItem). In that
            // case, just call it now so that this is empty at the end of this method.
            if (!val.setRemovalType(RemovalType.EXPLICIT)) {
                final boolean removed = this.clear(val);
                assert removed;
            }
        }
        assert this.size() == 0 : this + " expected to be empty but contains : " + this.cache.keySet();

        for (final CacheItem<K, V, D> val : new ArrayList<CacheItem<K, V, D>>(this.running.values()))
            val.setRemovalType(RemovalType.EXPLICIT);
        assert this.running.size() == 0 : this + " expected to have no running but contains : " + this.running.keySet();
    }

    private final void log(String msg, Object subject) {
        // do the toString() on subject only if necessary
        if (Log.get().isLoggable(LEVEL))
            Log.get().log(LEVEL, msg + ": " + subject);
    }

    public final synchronized int size() {
        return this.cache.size();
    }

    static public class Event<K, V, D> extends PropertyChangeEvent {

        public Event(ICache<K, V, D> source, String propertyName, Object oldValue, Object newValue) {
            super(source, propertyName, oldValue, newValue);
        }

        @SuppressWarnings("unchecked")
        @Override
        public ICache<K, V, D> getSource() {
            return (ICache<K, V, D>) super.getSource();
        }
    }

    private final Event<K, V, D> castEvent(final PropertyChangeEvent evt) {
        final Event<?, ?, ?> casted = (Event<?, ?, ?>) evt;
        // Needed sine this method can be called from outside this class
        // Only other option is to not use PropertyChangeSupport, and create our own generics-aware
        // version
        if (casted.getSource() != ICache.this)
            throw new IllegalArgumentException("Cannot uphold type safety");
        @SuppressWarnings("unchecked")
        final Event<K, V, D> res = (Event<K, V, D>) casted;
        return res;
    }

    private ItemEvent<K, V, D> createItemEvent(String propertyName, CacheItem<K, V, D> oldValue, CacheItem<K, V, D> newValue) {
        if (oldValue == null && newValue == null)
            throw new IllegalArgumentException("No values");
        assert (oldValue == null || oldValue.getCache() == this);
        assert (newValue == null || newValue.getCache() == this);
        return new ItemEvent<K, V, D>(this, propertyName, oldValue, newValue);
    }

    static public class ItemEvent<K, V, D> extends Event<K, V, D> {

        // ATTN doesn't check parameters
        private ItemEvent(ICache<K, V, D> source, String propertyName, CacheItem<K, V, D> oldValue, CacheItem<K, V, D> newValue) {
            super(source, propertyName, oldValue, newValue);
        }

        @SuppressWarnings("unchecked")
        @Override
        public CacheItem<K, V, D> getOldValue() {
            return (CacheItem<K, V, D>) super.getOldValue();
        }

        @SuppressWarnings("unchecked")
        @Override
        public CacheItem<K, V, D> getNewValue() {
            return (CacheItem<K, V, D>) super.getNewValue();
        }
    }

    public final PropertyChangeListener addItemListener(final IClosure<? super ItemEvent<K, V, D>> listener) {
        return addListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt instanceof ItemEvent)
                    listener.executeChecked((ItemEvent<K, V, D>) castEvent(evt));
            }
        });
    }

    public final PropertyChangeListener addListener(final IClosure<? super Event<K, V, D>> listener) {
        return addListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                listener.executeChecked(castEvent(evt));
            }
        });
    }

    public final PropertyChangeListener addListener(final PropertyChangeListener listener) {
        this.propSupp.addPropertyChangeListener(listener);
        return listener;
    }

    public final void removeListener(final PropertyChangeListener listener) {
        this.propSupp.removePropertyChangeListener(listener);
    }

    @Override
    public final String toString() {
        return this.toString(false);
    }

    public final String toString(final boolean withKeys) {
        final String keys;
        if (withKeys) {
            synchronized (this) {
                keys = ", keys cached: " + this.cache.keySet().toString();
            }
        } else {
            keys = "";
        }
        return this.getClass().getName() + " '" + this.getName() + "'" + keys;
    }
}