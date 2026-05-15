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

import org.jopendocument.util.ListMap;
import org.jopendocument.util.cache.CacheItem.RemovalType;
import org.jopendocument.util.cc.IdentityHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.jcip.annotations.GuardedBy;

/**
 * A watcher invalidates cache results when its data is modified.
 * 
 * @param <D> source data type, eg SQLTable.
 */
abstract public class CacheWatcher<D> {

    @GuardedBy("this")
    private final Set<CacheItem<?, ?, ? extends D>> values;
    @GuardedBy("this")
    private final ListMap<Object, CacheItem<?, ?, ? extends D>> additionalToNotify;
    private final D data;

    protected CacheWatcher(D data) {
        this.values = new IdentityHashSet<CacheItem<?, ?, ? extends D>>();
        this.additionalToNotify = ListMap.decorate(new IdentityHashMap<Object, List<CacheItem<?, ?, ? extends D>>>());
        this.data = data;
    }

    public final D getData() {
        return this.data;
    }

    final synchronized boolean isEmpty() {
        return this.values.isEmpty();
    }

    final synchronized boolean add(CacheItem<?, ?, ? extends D> key) {
        assert Thread.holdsLock(key) && key.getRemovalType() == null;
        final boolean wasEmpty = this.isEmpty();
        final boolean added = this.values.add(key);
        if (added) {
            for (final List<CacheItem<?, ?, ? extends D>> l : this.additionalToNotify.values())
                l.add(key);
            if (wasEmpty)
                this.startWatching();
        } else {
            assert !wasEmpty;
        }
        assert !this.isEmpty();
        return added;
    }

    final synchronized boolean remove(CacheItem<?, ?, ? extends D> key) {
        if (!this.isEmpty()) {
            for (final List<CacheItem<?, ?, ? extends D>> l : this.additionalToNotify.values())
                l.remove(key);
            final boolean res = this.values.remove(key);
            if (this.isEmpty())
                this.stopWatching();
            return res;
        } else {
            return false;
        }
    }

    protected void startWatching() {
    }

    protected void stopWatching() {
    }

    protected final void dataChanged(final Object event) {
        List<CacheItem<?, ?, ? extends D>> vals;
        synchronized (this) {
            final List<CacheItem<?, ?, ? extends D>> prev = this.additionalToNotify.putCollection(event, new ArrayList<CacheItem<?, ?, ? extends D>>());
            assert prev == null : "Duplicate event : " + event;
            vals = new ArrayList<CacheItem<?, ?, ? extends D>>(this.values);
        }

        try {
            fire(event, vals);
        } finally {
            // finally block to always remove from additionalToNotify
            synchronized (this) {
                final List<CacheItem<?, ?, ? extends D>> remove = this.additionalToNotify.remove(event);
                // can be null if we just died
                vals = remove == null ? Collections.<CacheItem<?, ?, ? extends D>> emptyList() : new ArrayList<CacheItem<?, ?, ? extends D>>(remove);
            }
            fire(event, vals);
        }
    }

    private void fire(final Object event, final Collection<CacheItem<?, ?, ? extends D>> vals) {
        assert !Thread.holdsLock(this);
        for (final CacheItem<?, ?, ? extends D> val : vals) {
            if (this.changedBy(val, event)) {
                val.setRemovalType(RemovalType.DATA_CHANGE);
            }
        }
    }

    protected boolean changedBy(CacheItem<?, ?, ? extends D> val, Object event) {
        return true;
    }

    @Override
    public String toString() {
        final String vals;
        synchronized (this) {
            vals = String.valueOf(this.values);
        }
        return this.getClass().getSimpleName() + " on " + getData() + " : " + vals;
    }
}