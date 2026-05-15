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

/**
 * Represent the result of a query to a cache. Allow one to know if the queried key was not in
 * cache, or the thread was interrupted or of course if the result was in cache.
 * 
 * @author Sylvain
 * 
 * @param <V> type of value.
 */
public final class CacheResult<V> {

    @SuppressWarnings("rawtypes")
    private static final CacheResult INTERRUPTED = new CacheResult(State.INTERRUPTED);
    @SuppressWarnings("rawtypes")
    private static final CacheResult NOT_IN_CACHE = new CacheResult(State.NOT_IN_CACHE);

    @SuppressWarnings("unchecked")
    static final <V> CacheResult<V> getNotInCache() {
        return NOT_IN_CACHE;
    }

    @SuppressWarnings("unchecked")
    static final <V> CacheResult<V> getInterrupted() {
        return INTERRUPTED;
    }

    static public enum State {
        VALID, NOT_IN_CACHE, INTERRUPTED
    };

    private final State state;
    // store the whole CacheValue (instead of just V) so that this it can be returned from
    // ICache.check() and passed to put()
    // no need to store the value as it is still accessible once the CacheValue is invalid
    private final CacheItem<?, V, ?> val;

    private CacheResult(final State state, final CacheItem<?, V, ?> val) {
        this.state = state;
        this.val = val;
    }

    CacheResult(final CacheItem<?, V, ?> val) {
        this(val.getState() == CacheItem.State.VALID ? State.VALID : State.NOT_IN_CACHE, val);
    }

    private CacheResult(State state) {
        this(state, null);
    }

    public V getRes() {
        if (this.state == State.VALID)
            return this.val.getValue();
        else
            throw new IllegalStateException(this + " is not valid : " + this.getState());
    }

    public State getState() {
        return this.state;
    }

    final CacheItem<?, V, ?> getVal() {
        return this.val;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " " + this.getState();
    }
}