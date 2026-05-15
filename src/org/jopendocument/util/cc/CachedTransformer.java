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

package org.jopendocument.util.cc;

import org.jopendocument.util.CollectionUtils;
import org.jopendocument.util.cache.LRUMap;
import org.jopendocument.util.cache.Memoizer;

import java.util.Map;

import net.jcip.annotations.NotThreadSafe;

/**
 * Allow to cache the result of a function.
 * 
 * @author sylvain
 *
 * @param <K> the type of the function parameter.
 * @param <V> the type of the function result.
 * @param <X> the type of the function exception.
 * @see LRUMap to limit cache size.
 * @see Memoizer for a thread-safe class with concurrent creation of entries.
 */
@NotThreadSafe
public class CachedTransformer<K, V, X extends Exception> implements ITransformerExn<K, V, X> {

    private final Map<K, V> map;
    private final boolean allowNullValue;
    private final ITransformerExn<K, V, X> function;

    public CachedTransformer(final Map<K, V> map, final ITransformerExn<K, V, X> function) {
        this(map, function, false);
    }

    public CachedTransformer(final Map<K, V> map, final ITransformerExn<K, V, X> function, final boolean allowNullValue) {
        this.map = map;
        this.function = function;
        this.allowNullValue = allowNullValue;
    }

    @Override
    public final V transformChecked(K key) throws X {
        return this.get(key);
    }

    public V get(K key) throws X {
        return CollectionUtils.computeIfAbsent(this.map, key, this.function, this.allowNullValue);
    }
}
