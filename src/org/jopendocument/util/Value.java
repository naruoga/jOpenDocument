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

import org.jopendocument.util.cc.I2ExnFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Null can be ambiguous, e.g. {@link Map#get(Object)}. This class allows to avoid the problem.
 * 
 * @author Sylvain
 * @param <V> type of value.
 */
public abstract class Value<V> {

    @SuppressWarnings("rawtypes")
    static private final Value NONE = new Value(false) {
        @Override
        public Object getValue() {
            throw new IllegalStateException(this.toString());
        }

        @Override
        public String toString() {
            return "No Value";
        }
    };

    /**
     * I.e. {@link Map#containsKey(Object)} is <code>false</code>.
     * 
     * @return the no value instance, i.e. {@link Value#hasValue()} <code>false</code>.
     */
    @SuppressWarnings("unchecked")
    public static <V> Value<V> getNone() {
        return NONE;
    }

    @SuppressWarnings("rawtypes")
    static private final Value NULL_VALUE = new Value.Some<>(null);

    /**
     * Return an instance with a <code>null</code> value.
     * 
     * @return the <code>null</code> {@link #getValue() value} instance, i.e.
     *         {@link Value#hasValue()} <code>true</code>.
     */
    @SuppressWarnings("unchecked")
    public static <V> Value<V> getNullValue() {
        return NULL_VALUE;
    }

    static private final class Some<V> extends Value<V> {

        private final V val;

        public Some(final V val) {
            super(true);
            this.val = val;
        }

        @Override
        public V getValue() {
            return this.val;
        }

        @Override
        public String toString() {
            return "Value <" + this.getValue() + '>';
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((this.val == null) ? 0 : this.val.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            final Some<?> other = (Some<?>) obj;
            return CompareUtils.equals(this.val, other.val);
        }
    };

    /**
     * I.e. {@link Map#containsKey(Object)} is <code>true</code>.
     * 
     * @param value e.g. the value contained in the Map, possibly <code>null</code>.
     * @return a instance with {@link #hasValue()} <code>true</code>.
     */
    public static <V> Value<V> getSome(final V value) {
        // less memory
        return value == null ? Value.<V> getNullValue() : new Value.Some<V>(value);
    }

    public static final boolean hasValue(final Value<?> v) {
        return v != null && v.hasValue();
    }

    /**
     * Usefull if <code>null</code> value actually means none, i.e. the map cannot contain
     * <code>null</code>.
     * 
     * @param value the value.
     * @return {@link #getNone()} if <code>value</code> is <code>null</code>,
     *         {@link #getSome(Object)} otherwise.
     */
    public static <V> Value<V> fromNonNull(final V value) {
        return value == null ? Value.<V> getNone() : getSome(value);
    }

    private final boolean hasValue;

    private Value(boolean hasValue) {
        super();
        this.hasValue = hasValue;
    }

    public final boolean hasValue() {
        return this.hasValue;
    }

    /**
     * Return our value if any.
     * 
     * @return our value if this {@link #hasValue()}, can be <code>null</code>.
     * @throws IllegalStateException if not {@link #hasValue()}.
     */
    public abstract V getValue() throws IllegalStateException;

    /**
     * Return the value if {@link #hasValue()}, otherwise the passed one, never throws an exception.
     * 
     * @param def the default value.
     * @return either {@link #getValue()} or <code>def</code>.
     */
    public final V getValue(final V def) {
        if (this.hasValue())
            return this.getValue();
        else
            return def;
    }

    public final Value<V> asSome(final V def) {
        if (this.hasValue())
            return this;
        else
            return getSome(def);
    }

    // Same method names as Optional

    public final V orElse(final V def) {
        return this.getValue(def);
    }

    public final V orGet(final Supplier<? extends V> def) {
        if (this.hasValue())
            return this.getValue();
        else
            return def.get();
    }

    public final <X extends Exception, X2 extends Exception> V orThrowingGet(final I2ExnFactory<? extends V, X, X2> def) throws X, X2 {
        if (this.hasValue())
            return this.getValue();
        else
            return def.createChecked();
    }

    /**
     * Return <code>null</code> if and only if this has no value.
     * 
     * @return non <code>null</code> {@link #getValue()} if {@link #hasValue()}, otherwise
     *         <code>null</code>.
     * @throws IllegalStateException if {@link #getValue()} is <code>null</code>.
     * @see #fromNonNull(Object)
     */
    public final V toNonNull() throws IllegalStateException {
        if (!this.hasValue())
            return null;
        final V res = this.getValue();
        if (res == null)
            throw new IllegalStateException("Null value");
        return res;
    }

    @SuppressWarnings("unchecked")
    public final <T> Value<T> cast(final Class<T> clazz) {
        if (this.hasValue())
            clazz.cast(this.getValue());
        return (Value<T>) this;
    }

    @Override
    public int hashCode() {
        return this.hasValue ? 1231 : 1237;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return this.hasValue == ((Value<?>) obj).hasValue;
    }
}