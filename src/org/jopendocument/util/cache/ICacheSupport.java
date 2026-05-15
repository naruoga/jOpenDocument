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

import org.jopendocument.util.CollectionUtils;
import org.jopendocument.util.IScheduledFutureTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.jcip.annotations.GuardedBy;

/**
 * Allow to pool the timer thread and the watchers for multiple {@link ICache}. Pooling the timer
 * saves resources, but pooling the watchers makes sure no events are lost when copying a value from
 * a cache to another. E.g. a watcher listens to a data, the data changes and copies its listeners
 * and begins to notify them. If one listener (before the watcher) hangs for a while, and then a new
 * watcher listens to the same data, it will never be notified of the previous event, no matter how
 * long ago it was generated. The only other option would be to also listen to the source cache item
 * but this is complicated and would need more memory allocation for each item.
 * 
 * @author Sylvain
 * 
 * @param <D> source data type, e.g. SQLTable.
 */
public final class ICacheSupport<D> {

    private final String name;
    private final ScheduledThreadPoolExecutor timer;
    @GuardedBy("this")
    private CacheWatcherFactory<? super D> watcherFactory;
    @GuardedBy("this")
    private final Map<D, CacheWatcher<? super D>> watchers;

    public ICacheSupport(final String name) {
        this(name, 2, TimeUnit.MINUTES);
    }

    public ICacheSupport(final String name, final long amout, final TimeUnit unit) {
        this.name = name;
        this.timer = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread res = new Thread(r, "cache timeout thread for " + getName());
                res.setDaemon(true);
                res.setPriority(Thread.MIN_PRIORITY);
                return res;
            }
        }) {
            @Override
            protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
                return new IScheduledFutureTask<V>(task).setInnerRunnable(runnable);
            }

            @Override
            protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
                return new IScheduledFutureTask<V>(task).setInnerCallable(callable);
            }
        };
        this.timer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                purgeWatchers();
            }
        }, amout, amout, unit);

        this.watcherFactory = null;
        this.watchers = new HashMap<D, CacheWatcher<? super D>>();
    }

    public final String getName() {
        return this.name;
    }

    public boolean die() {
        final boolean didDie;
        final List<Runnable> runnables;
        synchronized (this) {
            didDie = !this.isDying();
            if (didDie) {
                runnables = this.getTimer().shutdownNow();
            } else {
                runnables = null;
            }
        }

        boolean interrupted = false;
        if (didDie) {
            // only CacheTimeOut are in our executor (plus the runnable for trimWatchers())
            // and all items in a cache (even the running ones) have a timeout (but they don't all
            // have a watcher : watcherFactory can be null and an item can have no data)
            for (final Runnable r : runnables) {
                final IScheduledFutureTask<?> sft = (IScheduledFutureTask<?>) r;
                if (sft.getInner() instanceof CacheTimeOut) {
                    sft.getInnerRunnable().run();
                }
            }

            // make sure that the currently executing runnable is done before checking
            while (true) {
                try {
                    if (this.getTimer().awaitTermination(1, TimeUnit.SECONDS))
                        break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            synchronized (this) {
                purgeWatchers();
                assert this.watchers.isEmpty() : this.watchers.size() + " item(s) were not removed : " + this.watchers.values();
            }
        }

        if (interrupted)
            Thread.currentThread().interrupt();
        return didDie;
    }

    public final boolean isDying() {
        return this.getTimer().isShutdown();
    }

    final ScheduledExecutorService getTimer() {
        return this.timer;
    }

    public final void purgeTimer() {
        this.timer.purge();
    }

    public synchronized void setWatcherFactory(CacheWatcherFactory<? super D> watcherFactory) {
        if (watcherFactory == null)
            throw new NullPointerException("Null factory");
        if (this.watcherFactory != null)
            throw new IllegalStateException("Already set to " + this.watcherFactory);
        this.watcherFactory = watcherFactory;
    }

    final synchronized CacheWatcher<? super D> watch(D data, final CacheItem<?, ?, D> item) {
        if (this.watcherFactory == null)
            return null;
        if (this.isDying())
            throw new RejectedExecutionException("Dead support");
        CacheWatcher<? super D> watcher = this.watchers.get(data);
        if (watcher == null) {
            try {
                watcher = this.watcherFactory.createWatcher(data);
            } catch (Exception e) {
                throw new IllegalStateException("Couldn't create watcher for " + data, e);
            }
            this.watchers.put(data, watcher);
        }
        watcher.add(item);
        return watcher;
    }

    final synchronized Map<D, CacheWatcher<? super D>> watch(Set<? extends D> data, final CacheItem<?, ?, D> item) {
        final Map<D, CacheWatcher<? super D>> res = CollectionUtils.newLinkedHashMap(data.size());
        for (final D d : data) {
            final CacheWatcher<? super D> watcher = this.watch(d, item);
            if (watcher != null)
                res.put(d, watcher);
        }
        return Collections.unmodifiableMap(res);
    }

    public final synchronized int getWatchersCount() {
        return this.watchers.size();
    }

    public final synchronized int purgeWatchers() {
        final Iterator<Entry<D, CacheWatcher<? super D>>> iter = this.watchers.entrySet().iterator();
        while (iter.hasNext()) {
            final Entry<D, CacheWatcher<? super D>> e = iter.next();
            if (e.getValue().isEmpty())
                iter.remove();
        }
        return this.getWatchersCount();
    }

    final synchronized boolean dependsOn(D data) {
        return this.watchers.containsKey(data) && !this.watchers.get(data).isEmpty();
    }
}
