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

import org.jopendocument.util.CompareUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Allow to create a proxy object which uses a {@link HashingStrategy}.
 * 
 * @author Sylvain
 */
public class CustomEquals {

    static public final <T> boolean nullSafeEquals(final T o1, final T o2, final BiPredicate<? super T, ? super T> equal) {
        if (o1 == null)
            return o2 == null;
        else if (o2 == null)
            return false;
        else
            return equal.test(o1, o2);
    }

    static private final HashingStrategy<Object> DEFAULT = new HashingStrategy<Object>() {
        @Override
        public boolean equals(Object object1, Object object2) {
            return CompareUtils.equals(object1, object2);
        }

        @Override
        public int computeHashCode(Object object) {
            return object == null ? 0 : object.hashCode();
        }
    };

    static private final HashingStrategy<Object> IDENTITY = new HashingStrategy<Object>() {
        @Override
        public boolean equals(Object object1, Object object2) {
            return object1 == object2;
        }

        @Override
        public int computeHashCode(Object object) {
            return System.identityHashCode(object);
        }
    };

    static public HashingStrategy<Object> getDefault() {
        return DEFAULT;
    }

    static public HashingStrategy<Object> getIdentity() {
        return IDENTITY;
    }

    // only create sets if necessary
    static public final <S, T1 extends S, T2 extends S> boolean equals(final Set<T1> s1, final Set<T2> s2, final HashingStrategy<S> s) {
        return equals(s1, s2, s, true);
    }

    static public final <S, T1 extends S, T2 extends S> boolean equals(final List<T1> s1, final List<T2> s2, final HashingStrategy<S> s) {
        return equals(s1, s2, s, false);
    }

    /**
     * Test equality of 2 collections using the passed strategy.
     * 
     * @param <S> type of strategy.
     * @param <T1> type of first collection.
     * @param <T2> type of second collection.
     * @param s1 the first collection.
     * @param s2 the second collection.
     * @param s the hashing strategy, <code>null</code> meaning the {@link #getDefault() default}.
     * @param set <code>true</code> if the passed collections should be used as a {@link Set},
     *        <code>false</code> for a {@link List}.
     * @return <code>true</code> if the 2 collections are equal using the passed parameters.
     */
    static public final <S, T1 extends S, T2 extends S> boolean equals(final Collection<T1> s1, final Collection<T2> s2, final HashingStrategy<S> s, final boolean set) {
        final Collection<?> sA, sB;
        // if the caller ask that the collections are compared using Set or List make sure that they
        // already are.
        final Class<?> clazz = set ? Set.class : List.class;
        if ((s == null || s == DEFAULT) && clazz.isInstance(s1) && clazz.isInstance(s2)) {
            sA = s1;
            sB = s2;
            // boolean to only create collections if necessary (don't make callers create the empty
            // collections)
        } else if (set) {
            sA = ProxyFull.createSet(s, s1);
            sB = ProxyFull.createSet(s, s2);
        } else {
            sA = ProxyFull.createList(s, s1);
            sB = ProxyFull.createList(s, s2);
        }
        assert clazz.isInstance(sA) && clazz.isInstance(sB);
        return CompareUtils.equals(sA, sB);
    }

    static public final <S, T1 extends S> int indexOf(final List<T1> s1, final S o, final HashingStrategy<S> s) {
        return indexOf(s1, o, s::equals);
    }

    static public final <S, T1 extends S> int indexOf(final List<T1> s1, final S o, final BiPredicate<S, S> s) {
        if (s1 instanceof RandomAccess) {
            final int stop = s1.size();
            for (int i = 0; i < stop; i++) {
                if (s.test(s1.get(i), o))
                    return i;
            }
        } else {
            final ListIterator<T1> listIter = s1.listIterator();
            while (listIter.hasNext()) {
                final T1 item = listIter.next();
                if (s.test(item, o))
                    return listIter.previousIndex();
            }
        }
        return -1;
    }

    static public class ProxyFull<S, E extends S> implements ProxyItf<E> {

        static public final <S, E extends S> Set<ProxyFull<S, E>> createSet(final HashingStrategy<S> strategy, final Collection<E> coll) {
            return wrap(strategy, coll, new LinkedHashSet<ProxyFull<S, E>>());
        }

        static public final <S, E extends S> List<ProxyFull<S, E>> createList(final HashingStrategy<S> strategy, final Collection<E> coll) {
            return wrap(strategy, coll, new ArrayList<ProxyFull<S, E>>());
        }

        static public final <S, E extends S, C extends Collection<? super ProxyFull<S, E>>> C wrap(final HashingStrategy<S> strategy, final Collection<E> coll, final C res) {
            for (final E item : coll) {
                res.add(new ProxyFull<S, E>(item, strategy));
            }
            return res;
        }

        private final E delegate;
        private final HashingStrategy<S> strategy;

        /**
         * Create a proxy object that use a strategy to implement {@link #equals(Object)} and
         * {@link #hashCode()}.
         * 
         * @param delegate the object to use.
         * @param strategy the strategy, <code>null</code> meaning use default implementation.
         */
        public ProxyFull(final E delegate, final HashingStrategy<S> strategy) {
            this.delegate = delegate;
            /**
             * Allow null strategy since the caller doesn't have to deal with generic limitations :
             * 
             * <pre>
             * HashingStrategy&lt;Constraint&gt; strategy = sameSystem ? null : Constraint.getInterSystemHashStrategy();
             * </pre>
             * 
             * Otherwise if one have HashingStrategy&lt;Object&gt; and
             * HashingStrategy&lt;Constraint&gt; ProxyFull.createSet() can't be called since no Java
             * type can hold both those instances. In this class the two cases are handled
             * explicitly.
             */
            this.strategy = strategy;
        }

        @Override
        public final E getDelegate() {
            return this.delegate;
        }

        @Override
        public final HashingStrategy<S> getStrategy() {
            return this.strategy;
        }

        private final HashingStrategy<?> getInternalStrategy() {
            return this.strategy != null ? this.strategy : DEFAULT;
        }

        @Override
        public int hashCode() {
            // don't use getInternalStrategy() to avoid java warning
            if (this.strategy == null)
                return DEFAULT.computeHashCode(this.getDelegate());
            else
                return this.getStrategy().computeHashCode(this.getDelegate());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof ProxyFull))
                return false;
            final ProxyFull<?, ?> other = (ProxyFull<?, ?>) obj;
            // null and DEFAULT mean the same
            if (other.getInternalStrategy() != this.getInternalStrategy())
                return false;
            // OK since same instance of strategy
            @SuppressWarnings("unchecked")
            final S delegate2 = (S) other.getDelegate();
            final E delegate = this.getDelegate();
            if (delegate == delegate2)
                return true;
            // don't use getInternalStrategy() to avoid java warning
            if (this.strategy == null)
                return DEFAULT.equals(this.getDelegate(), delegate2);
            else
                return this.strategy.equals(this.getDelegate(), delegate2);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " using strategy " + this.getStrategy() + " for " + this.getDelegate();
        }
    }

    /**
     * A proxy object which uses a {@link HashingStrategy}.
     * 
     * @author Sylvain
     * @param <E> type of item
     */
    static public interface ProxyItf<E> {
        public E getDelegate();

        public HashingStrategy<? super E> getStrategy();
    }

    static public final <S, E extends S> Set<ProxyItf<E>> createSet(final HashingStrategy<S> strategy, final Collection<E> coll) {
        return ProxyFull.wrap(strategy, coll, new LinkedHashSet<ProxyItf<E>>());
    }

    static public final <S, E extends S> List<ProxyItf<E>> createList(final HashingStrategy<S> strategy, final Collection<E> coll) {
        return ProxyFull.wrap(strategy, coll, new ArrayList<ProxyItf<E>>());
    }

    static public final <T> Set<T> unwrapToSet(final Collection<? extends ProxyItf<T>> coll) {
        return unwrap(coll, new LinkedHashSet<T>());
    }

    static public final <T> List<T> unwrapToList(final Collection<? extends ProxyItf<T>> coll) {
        return unwrap(coll, new ArrayList<T>());
    }

    static public final <T, C extends Collection<? super T>> C unwrap(final Collection<? extends ProxyItf<T>> coll, final C res) {
        for (final ProxyItf<T> item : coll) {
            res.add(item.getDelegate());
        }
        return res;
    }

    /**
     * A simple subclass to save some typing and improve legibility.
     * 
     * @author Sylvain
     * @param <E> type of item
     */
    static public class Proxy<E> extends ProxyFull<E, E> {

        public Proxy(E delegate, HashingStrategy<E> strategy) {
            super(delegate, strategy);
        }
    }

    private CustomEquals() {
    }
}
