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

import org.jopendocument.util.Value;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.jcip.annotations.GuardedBy;

/**
 * Encapsulate all the data about a {@link ICache cache} item.
 * 
 * @author Sylvain
 * 
 * @param <K> key type, e.g. String.
 * @param <V> value type, e.g. List of SQLRow.
 * @param <D> source data type, e.g. SQLTable.
 */
public final class CacheItem<K, V, D> {

    /**
     * The states a value can be in. NOTE: not all instances go through all states, a value can
     * start as either {@link #CREATED} or {@link #VALID}, and it can go directly to
     * {@link #INVALID}.
     * 
     * @author Sylvain
     */
    static public enum State {
        /**
         * The item was just created with no value set and no other objects reference it.
         */
        CREATED(false),
        /**
         * The item has no value set but it listens to data change.
         */
        RUNNING(true),
        /**
         * The item has a {@link CacheItem#getValue() value} and hasn't been
         * {@link CacheItem#setRemovalType(RemovalType) invalidated}
         */
        VALID(true),
        /**
         * The item has been {@link CacheItem#setRemovalType(RemovalType) invalidated}
         */
        INVALID(false);

        private final boolean active;

        private State(final boolean active) {
            this.active = active;
        }

        public final boolean isActive() {
            return this.active;
        }
    }

    /**
     * The reason why {@link CacheItem#getState()} is {@link State#INVALID}.
     * 
     * @author Sylvain
     */
    static public enum RemovalType {
        TIMEOUT, DATA_CHANGE, SIZE_LIMIT, EXPLICIT, CACHE_DEATH
    }

    private final ICache<K, V, D> cache;
    private final K key;
    private final Set<D> data;
    @GuardedBy("this")
    private Value<V> value;
    @GuardedBy("this")
    private RemovalType removalType;
    @GuardedBy("this")
    private ScheduledFuture<?> timeout;
    @GuardedBy("this")
    private Map<D, CacheWatcher<? super D>> watchers;

    // running constructor
    CacheItem(final ICache<K, V, D> cache, final K key, final Set<? extends D> data) {
        this(cache, key, Value.<V> getNone(), data);

    }

    // valid constructor
    CacheItem(final ICache<K, V, D> cache, final K key, final V value, final Set<? extends D> data) {
        this(cache, key, Value.getSome(value), data);
    }

    private CacheItem(final ICache<K, V, D> cache, final K key, final Value<V> value, final Set<? extends D> data) {
        this.cache = cache;
        this.key = key;
        this.value = value;
        this.data = Collections.unmodifiableSet(new LinkedHashSet<D>(data));
        this.removalType = null;
    }

    public final ICache<K, V, D> getCache() {
        return this.cache;
    }

    public final Set<D> getData() {
        return this.data;
    }

    final Map<D, CacheWatcher<? super D>> addToWatchers() {
        final ICacheSupport<D> supp = this.getCache().getSupp();
        Map<D, CacheWatcher<? super D>> watchers = null;
        synchronized (this) {
            if (this.getRemovalType() == null && this.watchers == null) {
                try {
                    watchers = supp.watch(this.getData(), this);
                    this.watchers = watchers;
                    assert watchers != null;
                } catch (RejectedExecutionException e) {
                    this.setRemovalType(RemovalType.CACHE_DEATH);
                    assert watchers == null;
                }
            }
        }
        return watchers;
    }

    /**
     * Return an immutable map of our watchers.
     * 
     * @return an immutable map of our watchers.
     */
    public synchronized Map<D, CacheWatcher<? super D>> getWatchers() {
        return this.watchers;
    }

    final void addTimeout(final long delay, final TimeUnit unit) {
        final ScheduledExecutorService timer = this.getCache().getSupp().getTimer();
        synchronized (this) {
            if (this.getRemovalType() != null)
                return;
            if (this.timeout != null)
                throw new IllegalStateException("Already has a timeout : " + this.timeout);
            try {
                this.timeout = timer.schedule(new CacheTimeOut(this), delay, unit);
                assert this.timeout != null;
            } catch (RejectedExecutionException e) {
                this.setRemovalType(RemovalType.CACHE_DEATH);
                assert this.timeout == null;
            }
        }
    }

    public final long getRemainingTimeoutDelay() {
        synchronized (this) {
            if (this.getRemovalType() != null || this.timeout == null)
                return -1;
            return this.timeout.getDelay(TimeUnit.MILLISECONDS);
        }
    }

    public final boolean setRemovalType(final RemovalType type) {
        if (type == null)
            throw new NullPointerException("Null cause");
        final boolean notYetRemoved;
        synchronized (this) {
            notYetRemoved = this.getRemovalType() == null;
            if (notYetRemoved) {
                this.removalType = type;
                if (this.timeout != null)
                    this.timeout.cancel(false);
                if (this.watchers != null) {
                    for (final CacheWatcher<? super D> watcher : this.watchers.values()) {
                        watcher.remove(this);
                    }
                }
            }
        }
        if (notYetRemoved) {
            this.getCache().clear(this);
        }
        return notYetRemoved;
    }

    public final synchronized RemovalType getRemovalType() {
        return this.removalType;
    }

    public final K getKey() {
        return this.key;
    }

    public final synchronized State getState() {
        if (this.getRemovalType() != null)
            return State.INVALID;
        else if (this.value.hasValue())
            return State.VALID;
        else if (this.timeout != null || this.watchers != null)
            return State.RUNNING;
        else
            return State.CREATED;
    }

    final synchronized void setValue(final V value) {
        if (this.value.hasValue())
            throw new IllegalArgumentException("Already set");
        this.value = Value.getSome(value);
    }

    public final synchronized V getValue() {
        return this.value.getValue();
    }

    public final synchronized CacheResult<V> getResult() {
        if (this.getState() == State.VALID)
            return new CacheResult<V>(this);
        else
            return CacheResult.getNotInCache();
    }

    @Override
    public String toString() {
        final String val;
        final State state;
        synchronized (this) {
            val = this.value.toString();
            state = this.getState();
        }
        return this.getClass().getSimpleName() + " (" + state + ") for " + this.getKey() + " : " + val;
    }
}