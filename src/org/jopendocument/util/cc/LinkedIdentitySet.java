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

import java.util.Collection;
import java.util.LinkedList;

/**
 * An IdentitySet maintaining insertion order.
 * 
 * @param <E> the type of elements maintained by this set
 * @see IdentityHashSet
 */
public final class LinkedIdentitySet<E> extends ListIdentitySet<E> implements Cloneable {

    public LinkedIdentitySet() {
        super();
    }

    public LinkedIdentitySet(int expectedSize) {
        super(expectedSize);
    }

    public LinkedIdentitySet(Collection<? extends E> c) {
        super(c);
    }

    @Override
    protected LinkedList<E> newList(int expectedSize) {
        return new LinkedList<>();
    }

    /**
     * Returns a shallow copy of this <tt>HashSet</tt> instance: the elements themselves are not
     * cloned.
     * 
     * @return a shallow copy of this set
     */
    @Override
    public Object clone() {
        return new LinkedIdentitySet<E>(this);
    }

}
