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

import java.util.ArrayList;
import java.util.Collection;

/**
 * This class implements the <tt>Set</tt> interface with a list, using reference-equality in place
 * of object-equality when comparing items.
 * 
 * @param <E> the type of elements maintained by this set
 */
public final class ArrayIdentitySet<E> extends ListIdentitySet<E> implements Cloneable {

    public ArrayIdentitySet() {
        super();
    }

    public ArrayIdentitySet(int expectedMaxSize) {
        super(expectedMaxSize);
    }

    public ArrayIdentitySet(Collection<? extends E> c) {
        super(c);
    }

    @Override
    protected ArrayList<E> newList(int expectedSize) {
        return new ArrayList<>(expectedSize);
    }

    /**
     * Returns a shallow copy of this instance: the elements themselves are not cloned.
     * 
     * @return a shallow copy of this set
     */
    @Override
    public Object clone() {
        return new ArrayIdentitySet<E>(this);
    }
}
