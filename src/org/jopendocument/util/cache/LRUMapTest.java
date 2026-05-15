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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jopendocument.util.CollectionUtils;

import org.junit.jupiter.api.Test;

public class LRUMapTest {
    @Test
    public void test() {
        final LRUMap<String, String> m = new LRUMap<>(3);
        assertEquals(3, m.getMaxSize());
        assertEquals(0, m.size());

        m.put("1", null);
        m.put("2", null);
        m.put("3", null);
        assertEquals(3, m.size());

        // the eldest is 1
        m.put("4", null);
        assertEquals(CollectionUtils.createSet("2", "3", "4"), m.keySet());

        // accessing a value change the eldest
        m.get("2");
        m.put("5", null);
        assertEquals(CollectionUtils.createSet("2", "4", "5"), m.keySet());
    }
}
