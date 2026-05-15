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

package org.jopendocument.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A RunnableScheduledFuture for use in
 * {@link ScheduledThreadPoolExecutor#decorateTask(Runnable, RunnableScheduledFuture)}. It's needed
 * because since Java 7, the runnables returned by <code>shutdownNow()</code> can't be run.
 * 
 * @author Sylvain
 * 
 * @param <V> The result type returned by the <tt>get</tt> method
 */
public class IScheduledFutureTask<V> implements RunnableScheduledFuture<V> {

    private final RunnableScheduledFuture<V> delegate;
    private Object inner;

    public IScheduledFutureTask(final RunnableScheduledFuture<V> delegate) {
        this.delegate = delegate;
    }

    public final IScheduledFutureTask<V> setInnerRunnable(Runnable inner) {
        // perhaps use 2 variables (avoids casting) or use a boolean
        if (inner instanceof Callable)
            throw new IllegalArgumentException("Must call setInnerCallable() to check the generic parameter");
        return this.setInner(inner);
    }

    public final IScheduledFutureTask<V> setInnerCallable(Callable<V> innerCallable) {
        return this.setInner(innerCallable);
    }

    private final IScheduledFutureTask<V> setInner(Object inner) {
        if (inner == null)
            throw new NullPointerException();
        synchronized (this) {
            if (this.inner != null)
                throw new IllegalStateException("Already set");
            this.inner = inner;
        }
        return this;
    }

    public final Object getInner() {
        return this.inner;
    }

    public synchronized final Runnable getInnerRunnable() {
        return (Runnable) this.inner;
    }

    @SuppressWarnings("unchecked")
    public synchronized final Callable<V> getInnerCallable() {
        return (Callable<V>) this.inner;
    }

    @Override
    public long getDelay(final TimeUnit unit) {
        return this.delegate.getDelay(unit);
    }

    @Override
    public void run() {
        if (this.getInner() == null)
            throw new IllegalStateException("Inner callable not set");
        this.delegate.run();
    }

    @Override
    public boolean isPeriodic() {
        return this.delegate.isPeriodic();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return this.delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public int compareTo(final Delayed o) {
        return this.delegate.compareTo(o);
    }

    @Override
    public boolean isCancelled() {
        return this.delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return this.delegate.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return this.delegate.get();
    }

    @Override
    public V get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return this.delegate.get(timeout, unit);
    }
}
