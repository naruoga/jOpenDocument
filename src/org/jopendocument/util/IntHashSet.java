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

package org.jopendocument.util;

import java.util.Arrays;
import java.util.Collection;

/**
 * A type-specific hash set with with a fast, small-footprint implementation.
 *
 * <p>
 * Instances of this class use a hash table to represent a set. The table is filled up to a
 * specified <em>load factor</em>, and then doubled in size to accommodate new entries. If the table
 * is emptied below <em>one fourth</em> of the load factor, it is halved in size; however, the table
 * is never reduced to a size smaller than that at creation time: this approach makes it possible to
 * create sets with a large capacity in which insertions and deletions do not cause immediately
 * rehashing. Moreover, halving is not performed when deleting entries from an iterator, as it would
 * interfere with the iteration process.
 *
 * <p>
 * Note that {@link #clear()} does not modify the hash table size. Rather, a family of
 * {@linkplain #trim() trimming methods} lets you control the size of the table; this is
 * particularly useful if you reuse instances of this class.
 *
 */
public class IntHashSet {

    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_INITIAL_SIZE = 16;

    /** 2<sup>32</sup> &middot; &phi;, &phi; = (&#x221A;5 &minus; 1)/2. */
    private static final int INT_PHI = 0x9E3779B9;
    /** The reciprocal of {@link #INT_PHI} modulo 2<sup>32</sup>. */
    private static final int INV_INT_PHI = 0x144cbc89;

    /** The array of keys. */
    protected transient int[] key;
    /** The mask for wrapping a position counter. */
    protected transient int mask;
    /** Whether this set contains the null key. */
    protected transient boolean containsNull;
    /**
     * The current table size. Note that an additional element is allocated for storing the null
     * key.
     */
    protected transient int n;
    /** Threshold after which we rehash. It must be the table size times {@link #f}. */
    protected transient int maxFill;
    /** We never resize below this threshold, which is the construction-time {#n}. */
    protected final transient int minN;
    /** Number of entries in the set (including the null key, if present). */
    protected int size;
    /** The acceptable load factor. */
    protected final float f;

    /**
     * Creates a new hash set.
     *
     * <p>
     * The actual table size will be the least power of two greater than {@code expected}/{@code f}.
     *
     * @param expected the expected number of elements in the hash set.
     * @param f the load factor.
     */

    public IntHashSet(final int expected, final float f) {
        if (f <= 0 || f >= 1)
            throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than 1");
        if (expected < 0)
            throw new IllegalArgumentException("The expected number of elements must be nonnegative");
        this.f = f;
        minN = n = arraySize(expected, f);
        mask = n - 1;
        maxFill = maxFill(n, f);
        key = new int[n + 1];
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
     *
     * @param expected the expected number of elements in the hash set.
     */
    public IntHashSet(final int expected) {
        this(expected, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new hash set with initial expected {@link Hash#DEFAULT_INITIAL_SIZE} elements and
     * {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
     */
    public IntHashSet() {
        this(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new hash set copying a given collection.
     *
     * @param c a {@link Collection} to be copied into the new hash set.
     * @param f the load factor.
     */
    public IntHashSet(final Collection<? extends Integer> c, final float f) {
        this(c.size(), f);
        addAll(c);
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor copying a given
     * collection.
     *
     * @param c a {@link Collection} to be copied into the new hash set.
     */
    public IntHashSet(final Collection<? extends Integer> c) {
        this(c, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new hash set and fills it with the elements of a given array.
     *
     * @param a an array whose elements will be used to fill the set.
     * @param offset the first element to use.
     * @param length the number of elements to use.
     * @param f the load factor.
     */
    public IntHashSet(final int[] a, final int offset, final int length, final float f) {
        this(length < 0 ? 0 : length, f);
        ensureOffsetLength(a.length, offset, length);
        for (int i = 0; i < length; i++)
            add(a[offset + i]);
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor and fills it with
     * the elements of a given array.
     *
     * @param a an array whose elements will be used to fill the set.
     * @param offset the first element to use.
     * @param length the number of elements to use.
     */
    public IntHashSet(final int[] a, final int offset, final int length) {
        this(a, offset, length, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new hash set copying the elements of an array.
     *
     * @param a an array to be copied into the new hash set.
     * @param f the load factor.
     */
    public IntHashSet(final int[] a, final float f) {
        this(a, 0, a.length, f);
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor copying the
     * elements of an array.
     *
     * @param a an array to be copied into the new hash set.
     */
    public IntHashSet(final int[] a) {
        this(a, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new empty hash set.
     *
     * @return a new empty hash set.
     */
    public static IntHashSet of() {
        return new IntHashSet();
    }

    /**
     * Returns the least power of two smaller than or equal to 2<sup>30</sup> and larger than or
     * equal to {@code Math.ceil(expected / f)}.
     *
     * @param expected the expected number of elements in a hash table.
     * @param f the load factor.
     * @return the minimum possible size for a backing array.
     * @throws IllegalArgumentException if the necessary size is larger than 2<sup>30</sup>.
     */
    public static int arraySize(final int expected, final float f) {
        final long s = Math.max(2, nextPowerOfTwo((long) Math.ceil(expected / f)));
        if (s > (1 << 30))
            throw new IllegalArgumentException("Too large (" + expected + " expected elements with load factor " + f + ")");
        return (int) s;
    }

    /**
     * Returns the maximum number of entries that can be filled before rehashing.
     *
     * @param n the size of the backing array.
     * @param f the load factor.
     * @return the maximum number of entries before rehashing.
     */
    public static int maxFill(final int n, final float f) {
        /*
         * We must guarantee that there is always at least one free entry (even with pathological
         * load factors).
         */
        return Math.min((int) Math.ceil(n * f), n - 1);
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using the given
     * element.
     *
     * @param e the element that the returned set will contain.
     * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing
     *         {@code e}.
     */
    public static IntHashSet of(final int e) {
        IntHashSet result = new IntHashSet(1, DEFAULT_LOAD_FACTOR);
        result.add(e);
        return result;
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using the
     * elements given.
     *
     * @param e0 the first element.
     * @param e1 the second element.
     * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing
     *         {@code e0} and {@code e1}.
     * @throws IllegalArgumentException if there were duplicate entries.
     */
    public static IntHashSet of(final int e0, final int e1) {
        IntHashSet result = new IntHashSet(2, DEFAULT_LOAD_FACTOR);
        result.add(e0);
        if (!result.add(e1)) {
            throw new IllegalArgumentException("Duplicate element: " + e1);
        }
        return result;
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using the
     * elements given.
     *
     * @param e0 the first element.
     * @param e1 the second element.
     * @param e2 the third element.
     * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing
     *         {@code e0}, {@code e1}, and {@code e2}.
     * @throws IllegalArgumentException if there were duplicate entries.
     */
    public static IntHashSet of(final int e0, final int e1, final int e2) {
        IntHashSet result = new IntHashSet(3, DEFAULT_LOAD_FACTOR);
        result.add(e0);
        if (!result.add(e1)) {
            throw new IllegalArgumentException("Duplicate element: " + e1);
        }
        if (!result.add(e2)) {
            throw new IllegalArgumentException("Duplicate element: " + e2);
        }
        return result;
    }

    /**
     * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using a list of
     * elements.
     *
     * @param a a list of elements that will be used to initialize the new hash set.
     * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing the
     *         elements of {@code a}.
     * @throws IllegalArgumentException if a duplicate entry was encountered.
     */

    public static IntHashSet of(final int... a) {
        IntHashSet result = new IntHashSet(a.length, DEFAULT_LOAD_FACTOR);
        for (int element : a) {
            if (!result.add(element)) {
                throw new IllegalArgumentException("Duplicate element " + element);
            }
        }
        return result;
    }

    private int realSize() {
        return containsNull ? size - 1 : size;
    }

    private void ensureCapacity(final int capacity) {
        final int needed = arraySize(capacity, f);
        if (needed > n)
            rehash(needed);
    }

    private void tryCapacity(final long capacity) {
        final int needed = (int) Math.min(1 << 30, Math.max(2, nextPowerOfTwo((long) Math.ceil(capacity / f))));
        if (needed > n)
            rehash(needed);
    }

    public void addAll(Collection<? extends Integer> c) {
        // The resulting collection will be at least c.size() big
        if (f <= .5)
            ensureCapacity(c.size()); // The resulting collection will be sized for c.size()
                                      // elements
        else
            tryCapacity(size() + c.size()); // The resulting collection will be tentatively sized
                                            // for size() + c.size() elements
        for (Integer i : c) {
            add(i);
        }
    }

    /**
     * Quickly mixes the bits of an integer.
     *
     * <p>
     * This method mixes the bits of the argument by multiplying by the golden ratio and xorshifting
     * the result. It is borrowed from <a href="https://github.com/OpenHFT/Koloboke">Koloboke</a>,
     * and it has slightly worse behaviour than {@link #murmurHash3(int)} (in open-addressing hash
     * tables the average number of probes is slightly larger), but it's much faster.
     *
     * @param x an integer.
     * @return a hash value obtained by mixing the bits of {@code x}.
     * @see #invMix(int)
     */
    public static int mix(final int x) {
        final int h = x * INT_PHI;
        return h ^ (h >>> 16);
    }

    /**
     * The inverse of {@link #mix(int)}. This method is mainly useful to create unit tests.
     *
     * @param x an integer.
     * @return a value that passed through {@link #mix(int)} would give {@code x}.
     */
    public static int invMix(final int x) {
        return (x ^ x >>> 16) * INV_INT_PHI;
    }

    public boolean add(final int k) {
        int pos;
        if (k == 0) {
            if (containsNull)
                return false;
            containsNull = true;
        } else {
            int curr;
            final int[] key = this.key;
            // The starting point.
            if (!((curr = key[pos = (mix((k))) & mask]) == 0)) {
                if (curr == k)
                    return false;
                while (!((curr = key[pos = (pos + 1) & mask]) == 0))
                    if (((curr) == (k)))
                        return false;
            }
            key[pos] = k;
        }
        if (size++ >= maxFill)
            rehash(arraySize(size + 1, f));

        return true;
    }

    /**
     * Shifts left entries with the specified hash code, starting at the specified position, and
     * empties the resulting free entry.
     *
     * @param pos a starting position.
     */
    protected final void shiftKeys(int pos) {
        // Shift entries with the same hash.
        int last;
        int slot;
        int curr;
        final int[] key = this.key;
        for (;;) {
            pos = ((last = pos) + 1) & mask;
            for (;;) {
                if (((curr = key[pos]) == (0))) {
                    key[last] = (0);
                    return;
                }
                slot = (mix((curr))) & mask;
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos)
                    break;
                pos = (pos + 1) & mask;
            }
            key[last] = curr;
        }
    }

    private boolean removeEntry(final int pos) {
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE)
            rehash(n / 2);
        return true;
    }

    private boolean removeNullEntry() {
        containsNull = false;
        key[n] = (0);
        size--;
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE)
            rehash(n / 2);
        return true;
    }

    public boolean remove(final int k) {
        if (((k) == (0))) {
            if (containsNull)
                return removeNullEntry();
            return false;
        }
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (mix((k))) & mask]) == (0)))
            return false;
        if (((k) == (curr)))
            return removeEntry(pos);
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0)))
                return false;
            if (((k) == (curr)))
                return removeEntry(pos);
        }
    }

    public boolean contains(final int k) {
        if (((k) == (0)))
            return containsNull;
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (mix((k))) & mask]) == (0)))
            return false;
        if (((k) == (curr)))
            return true;
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0)))
                return false;
            if (((k) == (curr)))
                return true;
        }
    }

    /*
     * Removes all elements from this set.
     *
     * <p>To increase object reuse, this method does not change the table size. If you want to
     * reduce the table size, you must use {@link #trim()}.
     *
     */

    public void clear() {
        if (size == 0)
            return;
        size = 0;
        containsNull = false;
        Arrays.fill(key, (0));
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Rehashes this set, making the table as small as possible.
     *
     * <p>
     * This method rehashes the table to the smallest size satisfying the load factor. It can be
     * used when the set will not be changed anymore, so to optimize access speed and size.
     *
     * <p>
     * If the table size is already the minimum possible, this method does nothing.
     *
     * @return true if there was enough memory to trim the set.
     * @see #trim(int)
     */
    public boolean trim() {
        return trim(size);
    }

    /**
     * Rehashes this set if the table is too large.
     *
     * <p>
     * Let <var>N</var> be the smallest table size that can hold <code>max(n,{@link #size()})</code>
     * entries, still satisfying the load factor. If the current table size is smaller than or equal
     * to <var>N</var>, this method does nothing. Otherwise, it rehashes this set in a table of size
     * <var>N</var>.
     *
     * <p>
     * This method is useful when reusing sets. {@linkplain #clear() Clearing a set} leaves the
     * table size untouched. If you are reusing a set many times, you can call this method with a
     * typical size to avoid keeping around a very large table just because of a few large transient
     * sets.
     *
     * @param n the threshold for the trimming.
     * @return true if there was enough memory to trim the set.
     * @see #trim()
     */
    public boolean trim(final int n) {
        final int l = nextPowerOfTwo((int) Math.ceil(n / f));
        if (l >= this.n || size > maxFill(l, f))
            return true;
        try {
            rehash(l);
        } catch (OutOfMemoryError cantDoIt) {
            return false;
        }
        return true;
    }

    /**
     * Rehashes the set.
     *
     * <p>
     * This method implements the basic rehashing strategy, and may be overriden by subclasses
     * implementing different rehashing strategies (e.g., disk-based rehashing). However, you should
     * not override this method unless you understand the internal workings of this class.
     *
     * @param newN the new size
     */

    protected void rehash(final int newN) {
        final int key[] = this.key;
        final int mask = newN - 1; // Note that this is used by the hashing macro
        final int newKey[] = new int[newN + 1];
        int i = n, pos;
        for (int j = realSize(); j-- != 0;) {
            while (((key[--i]) == (0)))
                ;
            if (!((newKey[pos = (mix((key[i]))) & mask]) == (0)))
                while (!((newKey[pos = (pos + 1) & mask]) == (0)))
                    ;
            newKey[pos] = key[i];
        }
        n = newN;
        this.mask = mask;
        maxFill = maxFill(n, f);
        this.key = newKey;
    }

    /**
     * Returns a deep copy of this set.
     *
     * <p>
     * This method performs a deep copy of this hash set; the data stored in the set, however, is
     * not cloned. Note that this makes a difference only for object keys.
     *
     * @return a deep copy of this set.
     */
    @Override

    public IntHashSet clone() {
        IntHashSet c;
        try {
            c = (IntHashSet) super.clone();
        } catch (CloneNotSupportedException cantHappen) {
            throw new InternalError();
        }
        c.key = key.clone();
        c.containsNull = containsNull;
        return c;
    }

    /**
     * Returns a hash code for this set.
     *
     * This method overrides the generic method provided by the superclass. Since {@code equals()}
     * is not overriden, it is important that the value returned by this method is the same value as
     * the one returned by the overriden method.
     *
     * @return a hash code for this set.
     */
    @Override
    public int hashCode() {
        int h = 0;
        for (int j = realSize(), i = 0; j-- != 0;) {
            while (((key[i]) == (0)))
                i++;
            h += (key[i]);
            i++;
        }
        // Zero / null have hash zero.
        return h;
    }

    /**
     * Returns the least power of two greater than or equal to the specified value.
     *
     * <p>
     * Note that this function will return 1 when the argument is 0.
     *
     * @param x an integer smaller than or equal to 2<sup>30</sup>.
     * @return the least power of two greater than or equal to the specified value.
     */
    public static int nextPowerOfTwo(int x) {
        if (x == 0)
            return 1;
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        return (x | x >> 16) + 1;
    }

    /**
     * Returns the least power of two greater than or equal to the specified value.
     *
     * <p>
     * Note that this function will return 1 when the argument is 0.
     *
     * @param x a long integer smaller than or equal to 2<sup>62</sup>.
     * @return the least power of two greater than or equal to the specified value.
     */
    public static long nextPowerOfTwo(long x) {
        if (x == 0)
            return 1;
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return (x | x >> 32) + 1;
    }

    /**
     * Ensures that a range given by an offset and a length fits an array of given length.
     *
     * <p>
     * This method may be used whenever an array range check is needed.
     *
     * <p>
     * In Java 9 and up, this method should be considered deprecated in favor of the
     * {@link java.util.Objects#checkFromIndexSize(int, int, int)} method, which may be intrinsified
     * in recent JVMs.
     *
     * @param arrayLength an array length.
     * @param offset a start index for the fragment
     * @param length a length (the number of elements in the fragment).
     * @throws IllegalArgumentException if {@code length} is negative.
     * @throws ArrayIndexOutOfBoundsException if {@code offset} is negative or
     *         {@code offset}+{@code length} is greater than {@code arrayLength}.
     */
    public static void ensureOffsetLength(final int arrayLength, final int offset, final int length) {
        // When Java 9 becomes the minimum, use Objects#checkFromIndexSize​, as that can be an
        // intrinsic
        if (offset < 0)
            throw new ArrayIndexOutOfBoundsException("Offset (" + offset + ") is negative");
        if (length < 0)
            throw new IllegalArgumentException("Length (" + length + ") is negative");
        if (offset + length > arrayLength)
            throw new ArrayIndexOutOfBoundsException("Last index (" + (offset + length) + ") is greater than array length (" + arrayLength + ")");
    }

}
