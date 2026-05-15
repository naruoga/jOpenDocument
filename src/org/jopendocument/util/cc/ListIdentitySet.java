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
import org.jopendocument.util.IntHashSet;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * An IdentitySet maintaining insertion order.
 * 
 * @param <E> the type of elements maintained by this set
 * @see IdentityHashSet
 */
public abstract class ListIdentitySet<E> extends AbstractSet<E> implements IdentitySet<E> {

    static final int THRESHOLD = 16;

    private final List<E> list;
    private IntHashSet identityHashCodes;

    protected ListIdentitySet(final Collection<? extends E> c) {
        this(c.size());
        if (c instanceof ListIdentitySet) {
            final ListIdentitySet<? extends E> s = (ListIdentitySet<? extends E>) c;
            this.list.addAll(c);
            this.identityHashCodes = s.identityHashCodes;
        } else {
            this.addAll(c);
        }
    }

    protected ListIdentitySet() {
        this(16);
    }

    protected ListIdentitySet(final int expectedSize) {
        this.list = this.newList(expectedSize);
        this.identityHashCodes = null;
    }

    public final boolean hasHashCodes() {
        return this.identityHashCodes != null;
    }

    public final void initHashCodes() {
        if (this.identityHashCodes == null) {
            this.identityHashCodes = new IntHashSet(this.size());
            for (final E i : this.list) {
                this.identityHashCodes.add(System.identityHashCode(i));
            }
        }
    }

    final boolean sameSize() {
        return this.identityHashCodes == null || this.identityHashCodes.size() == this.list.size();
    }

    protected abstract List<E> newList(int expectedSize);

    public final List<E> getList() {
        return Collections.unmodifiableList(this.list);
    }

    @Override
    public final boolean add(E e) {
        if (this.identityHashCodes != null) {
            final boolean added = this.identityHashCodes.add(System.identityHashCode(e));
            if (added) {
                this.list.add(e);
            }
            assert sameSize();
            return added;
        } else {
            final boolean contains = this.contains(e);
            if (!contains) {
                this.list.add(e);
                if (this.size() > THRESHOLD) {
                    initHashCodes();
                }
            }
            assert sameSize();
            return !contains;
        }
    }

    @Override
    public final boolean addAll(Collection<? extends E> c) {
        // let the super which calls add() since we don't want to build a map to use Map.addAll()
        return super.addAll(c);
    }

    @Override
    public final void clear() {
        this.list.clear();
        this.identityHashCodes = null;
    }

    @Override
    public final int size() {
        return this.list.size();
    }

    @Override
    public final boolean isEmpty() {
        return this.list.isEmpty();
    }

    @Override
    public final Iterator<E> iterator() {
        if (this.identityHashCodes == null)
            return this.list.iterator();

        return new Iterator<E>() {

            private final Iterator<E> iter = ListIdentitySet.this.list.iterator();
            private E lastReturned = null;

            @Override
            public boolean hasNext() {
                return this.iter.hasNext();
            }

            @Override
            public E next() {
                this.lastReturned = this.iter.next();
                return this.lastReturned;
            }

            @Override
            public void remove() {
                this.iter.remove();
                ListIdentitySet.this.identityHashCodes.remove(System.identityHashCode(this.lastReturned));
                assert sameSize();
            }
        };
    }

    @Override
    public boolean contains(Object o) {
        if (this.identityHashCodes != null)
            return this.identityHashCodes.contains(System.identityHashCode(o));
        else
            return CollectionUtils.identityContains(this.list, o);
    }

    @Override
    public final boolean containsAll(Collection<?> c) {
        // let the super which calls contains()
        return super.containsAll(c);
    }

    @Override
    public boolean remove(Object o) {
        final boolean res;
        if (this.identityHashCodes != null) {
            res = this.identityHashCodes.remove(System.identityHashCode(o));
            if (res)
                CollectionUtils.identityRemove(this.list, o);
        } else {
            res = CollectionUtils.identityRemove(this.list, o);
        }
        assert sameSize();
        return res;
    }

    /*
     * (From IdentityHashMap) Must revert from AbstractSet's impl to AbstractCollection's, as the
     * former contains an optimization that results in incorrect behavior when c is a smaller
     * "normal" (non-identity-based) Set.
     */
    @Override
    public final boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Iterator<E> i = iterator(); i.hasNext();) {
            if (c.contains(i.next())) {
                i.remove();
                modified = true;
            }
        }
        assert sameSize();
        return modified;
    }

    @Override
    public final boolean retainAll(Collection<?> c) {
        // let the super which calls remove()
        return super.retainAll(c);
    }

    @Override
    public final Object[] toArray() {
        return this.list.toArray();
    }

    @Override
    public final <T> T[] toArray(T[] a) {
        return this.list.toArray(a);
    }

    @Override
    public final int hashCode() {
        int result = 0;
        for (E key : this.list)
            result += System.identityHashCode(key);
        return result;
    }
}
