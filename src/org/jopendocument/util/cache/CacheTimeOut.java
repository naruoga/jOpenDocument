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

import org.jopendocument.util.cache.CacheItem.RemovalType;

// allow to clear the cache after some period of time
final class CacheTimeOut implements Runnable {

    private final CacheItem<?, ?, ?> val;

    public CacheTimeOut(CacheItem<?, ?, ?> val) {
        this.val = val;
    }

    @Override
    public void run() {
        final boolean die = this.val.getCache().getSupp().isDying();
        this.val.setRemovalType(die ? RemovalType.CACHE_DEATH : RemovalType.TIMEOUT);
    }
}
