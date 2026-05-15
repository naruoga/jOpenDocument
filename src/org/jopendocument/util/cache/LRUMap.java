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

import java.util.LinkedHashMap;

public class LRUMap<K, V> extends LinkedHashMap<K, V> {

    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int REHASH_GROWTH = 2;
    private static final int MIN_CAPACITY = 4;
    private static final int SIZE_THRESHOLD = (int) (MIN_CAPACITY * DEFAULT_LOAD_FACTOR * REHASH_GROWTH);

    private int maxSize;

    public LRUMap(final int maxSize) {
        this(maxSize, maxSize <= SIZE_THRESHOLD ? MIN_CAPACITY : (int) (maxSize / DEFAULT_LOAD_FACTOR / REHASH_GROWTH + 1));
    }

    public LRUMap(final int maxSize, final int initialCapacity) {
        this(maxSize, initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public LRUMap(final int maxSize, int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
        this.setMaxSize(maxSize);
    }

    public final int getMaxSize() {
        return this.maxSize;
    }

    public final void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    protected final boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        return this.size() > this.getMaxSize();
    }
}
