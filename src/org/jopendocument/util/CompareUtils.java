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

/*
 * Créé le 14 avr. 2005
 */
package org.jopendocument.util;

import org.jopendocument.util.cc.ITransformer;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Sylvain CUAZ
 */
public class CompareUtils {

    static public final <A extends Comparable<A>, B extends Comparable<B>> int compare(final A a1, final B b1, final A a2, final B b2) {
        final int res = a1.compareTo(a2);
        if (res != 0)
            return res;
        return b1.compareTo(b2);
    }

    static public final <A extends Comparable<A>, B extends Comparable<B>, C extends Comparable<C>> int compare(final A a1, final B b1, final C c1, final A a2, final B b2, final C c2) {
        final int res = compare(a1, b1, a2, b2);
        if (res != 0)
            return res;
        return c1.compareTo(c2);
    }

    static public final <A extends Comparable<A>, B extends Comparable<B>, C extends Comparable<C>, D extends Comparable<D>> int compare(final A a1, final B b1, final C c1, final D d1, final A a2,
            final B b2, final C c2, final D d2) {
        final int res = compare(a1, b1, c1, a2, b2, c2);
        if (res != 0)
            return res;
        return d1.compareTo(d2);
    }

    static public final <A extends Comparable<A>, B extends Comparable<B>> int compare(final Tuple2<A, B> t1, final Tuple2<A, B> t2) {
        return compare(t1.get0(), t1.get1(), t2.get0(), t2.get1());
    }

    static public final <A extends Comparable<A>, B extends Comparable<B>, C extends Comparable<C>> int compare(final Tuple3<A, B, C> t1, final Tuple3<A, B, C> t2) {
        return compare(t1.get0(), t1.get1(), t1.get2(), t2.get0(), t2.get1(), t2.get2());
    }

    /**
     * Compare 2 nombres entier avec longValue().
     * 
     * @param n1 le premier nombre.
     * @param n2 le deuxième nombre.
     * @return 0 si ==, >0 si n1>2.
     */
    public static final int compareIntNumbers(Number n1, Number n2) {
        return compareLong(n1.longValue(), n2.longValue());
    }

    static public final int compareInt(int int1, int int2) {
        if (int1 < int2)
            return -1;
        else if (int1 == int2)
            return 0;
        else
            return +1;
    }

    static public final int compareLong(long int1, long int2) {
        if (int1 < int2)
            return -1;
        else if (int1 == int2)
            return 0;
        else
            return +1;
    }

    static public final <T extends Comparable<T>> int compareList(final List<? extends T> l1, final List<? extends T> l2) {
        return compareList(l1, l2, CompareUtils.<T> naturalOrder(), true);
    }

    static public final <T> int compareList(final List<? extends T> l1, final List<? extends T> l2, final Comparator<? super T> comp, final boolean longerAfter) {
        if (l1 == l2)
            return 0;

        final Iterator<? extends T> iter1 = l1.iterator();
        final Iterator<? extends T> iter2 = l2.iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            final T t1 = iter1.next();
            final T t2 = iter2.next();
            final int res = comp.compare(t1, t2);
            if (res != 0)
                return res;
        }
        if (!iter1.hasNext() && !iter2.hasNext()) {
            return 0;
        } else if (iter1.hasNext()) {
            return longerAfter ? 1 : -1;
        } else {
            assert iter2.hasNext();
            return longerAfter ? -1 : 1;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final Comparator LIST_COMPARATOR = new Comparator<List>() {
        @SuppressWarnings("unchecked")
        @Override
        public int compare(List l1, List l2) {
            return compareList(l1, l2);
        }
    };

    @SuppressWarnings("unchecked")
    static public final <T extends Comparable<T>> Comparator<List<T>> getListComparator() {
        return LIST_COMPARATOR;
    }

    static public final <T> Comparator<List<T>> createListComparator(final Comparator<? super T> comp, final boolean longerAfter) {
        return new Comparator<List<T>>() {
            @Override
            public int compare(List<T> o1, List<T> o2) {
                return compareList(o1, o2, comp, longerAfter);
            }
        };
    }

    /**
     * Compare two objects if they're numbers or comparable.
     * 
     * @param o1 first object.
     * @param o2 second object.
     * @return a negative integer, zero, or a positive integer as o1 is less than, equal to, or
     *         greater than o2.
     * @throws ClassCastException if o1 is neither a {@link Number} nor a {@link Comparable}, or if
     *         o2's type prevents it from being compared to o1.
     * @throws NullPointerException if o1 or o2 is <code>null</code>.
     * @see Comparable#compareTo(Object)
     * @see NumberUtils#compare(Number, Number)
     */
    static public final int compare(final Object o1, final Object o2) throws ClassCastException {
        if (o1 == null || o2 == null)
            throw new NullPointerException();
        if (o1 instanceof Number && o2 instanceof Number) {
            return NumberUtils.compare((Number) o1, (Number) o2);
        } else {
            // see Arrays.mergeSort()
            @SuppressWarnings({ "rawtypes", "unchecked" })
            final int res = ((Comparable) o1).compareTo(o2);
            return res;
        }
    }

    static private final Comparator<Comparable<Object>> NATURAL_COMPARATOR = new Comparator<Comparable<Object>>() {
        @Override
        public int compare(Comparable<Object> o1, Comparable<Object> o2) {
            return o1.compareTo(o2);
        }
    };

    // added in Comparator in Java 8
    @SuppressWarnings("unchecked")
    static public final <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (Comparator<T>) NATURAL_COMPARATOR;
    }

    /**
     * Renvoie un comparateur qui utilise successivement la liste passée tant que les objets sont
     * égaux.
     * 
     * @param comparators une liste de Comparator.
     * @return le Comparator demandé.
     * @param <T> type of comparator
     */
    static public final <T> Comparator<T> createComparator(final List<? extends Comparator<T>> comparators) {
        return new Comparator<T>() {
            public String toString() {
                return "CompareUtils comparator with " + comparators;
            }

            public int compare(T o1, T o2) {
                int result = 0;
                int i = 0;
                while (i < comparators.size() && result == 0) {
                    final Comparator<T> transf = comparators.get(i);
                    result = transf.compare(o1, o2);
                    i++;
                }
                return result;
            }
        };
    }

    /**
     * Compare 2 objets pouvant être <code>null</code>.
     * 
     * @param o1 the first object, can be <code>null</code>.
     * @param o2 the second object, can be <code>null</code>.
     * @return <code>true</code> if both are <code>null</code> or if o1.equals(o2).
     * @see Object#equals(Object)
     */
    static public final boolean equals(Object o1, Object o2) {
        if (o1 == null && o2 == null)
            return true;
        if (o1 == null || o2 == null)
            return false;
        return o1.equals(o2);
    }

    /**
     * Compare 2 objets pouvant être <code>null</code> avec compareTo(). Useful since for some
     * classes equals() is more specific than compareTo()==0, e.g. {@link BigDecimal#equals(Object)}
     * doesn't compare the numeric value but instance variables (1E2 is not equal to 100 or 100.00).
     * 
     * @param o1 the first object, can be <code>null</code>.
     * @param o2 the second object, can be <code>null</code>.
     * @return <code>true</code> if both are <code>null</code> or if o1.compareTo(o2) == 0.
     * @see Comparable#compareTo(Object)
     */
    static public final <T> boolean equalsWithCompareTo(Comparable<T> o1, T o2) {
        if (o1 == null && o2 == null)
            return true;
        if (o1 == null || o2 == null)
            return false;
        return o1.compareTo(o2) == 0;
    }

    /**
     * Compare 2 collections in order. Useful since for some classes even though there's an order
     * <code>equals()</code> ignores it (e.g. {@link LinkedHashMap#equals(Object)}).
     * 
     * @param c1 the first object, can be <code>null</code>.
     * @param c2 the second object, can be <code>null</code>.
     * @return <code>true</code> if both are <code>null</code> or if all items in order are
     *         {@link #equals(Object, Object)}.
     */
    static public final boolean equalsUsingIterator(Collection<?> c1, Collection<?> c2) {
        if (c1 == null && c2 == null)
            return true;
        if (c1 == null || c2 == null)
            return false;

        final int size = c1.size();
        if (size != c2.size())
            return false;
        else if (size == 0)
            return true;

        final Iterator<?> iter1 = c1.iterator();
        final Iterator<?> iter2 = c2.iterator();
        while (iter1.hasNext()) {
            final Object o1 = iter1.next();
            final Object o2 = iter2.next();
            if (!equals(o1, o2))
                return false;
        }
        assert !iter1.hasNext() && !iter2.hasNext();
        return true;
    }

    static public interface Equalizer<T> {
        public boolean equals(T o1, T o2);
    }

    static public final Equalizer<Object> OBJECT_EQ = new Equalizer<Object>() {
        public boolean equals(Object o1, Object o2) {
            return CompareUtils.equals(o1, o2);
        }
    };

    static public final <T> boolean equals(List<? extends T> l1, List<? extends T> l2, Equalizer<? super T> comp) {
        return compare(l1, l2, comp, null) == null;
    }

    /**
     * Compare two lists using the provided comparator.
     * 
     * @param <T> type of items
     * @param l1 the first list.
     * @param l2 the second list.
     * @param comp how to compare each item.
     * @param toString how to dispay items, can be <code>null</code>.
     * @return <code>null</code> if the two lists are equal, otherwise a String explaining the
     *         difference.
     */
    static public final <T> String compare(List<? extends T> l1, List<? extends T> l2, Equalizer<? super T> comp, final ITransformer<? super T, String> toString) {
        final int size = l1.size();
        if (size != l2.size())
            return "unequal size";
        for (int i = 0; i < size; i++) {
            final T o1 = l1.get(i);
            final T o2 = l2.get(i);
            if (!comp.equals(o1, o2)) {
                final String s1 = toString == null ? String.valueOf(o1) : toString.transformChecked(o1);
                final String s2 = toString == null ? String.valueOf(o2) : toString.transformChecked(o2);
                return "unequal at " + i + ": " + s1 + " != " + s2;
            }
        }
        return null;
    }

    static public final <T extends Comparable<? super T>> T min(final T o1, final T o2) {
        return o1.compareTo(o2) < 0 ? o1 : o2;
    }

    static public final <T extends Comparable<? super T>> T max(final T o1, final T o2) {
        return o1.compareTo(o2) < 0 ? o2 : o1;
    }
}