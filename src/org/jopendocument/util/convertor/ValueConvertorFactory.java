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

package org.jopendocument.util.convertor;

import org.jopendocument.util.ReflectUtils;
import org.jopendocument.util.Tuple2.List2;
import org.jopendocument.util.cache.LRUMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.jcip.annotations.GuardedBy;

public final class ValueConvertorFactory {

    @SuppressWarnings("rawtypes")
    private static final ValueConvertor IdentityConvertor = new ValueConvertor() {
        @Override
        public Object convert(Object o) {
            return o;
        }

        @Override
        public Object unconvert(Object o) {
            return o;
        }
    };
    private static final ValueConvertor<?, ?> MISSING_CONV = new ValueConvertor<Void, Void>() {
        @Override
        public Void convert(Void o) {
            return null;
        }

        @Override
        public Void unconvert(Void o) {
            return null;
        }
    };

    @SuppressWarnings("unchecked")
    public static final <T> ValueConvertor<T, T> getIdentityConvertor() {
        return IdentityConvertor;
    }

    private static final List<ValueConvertor<?, ?>> STRICT_CONVERTORS;
    private static final List<ValueConvertor<?, ?>> LENIENT_CONVERTORS;
    @GuardedBy("self")
    private static Map<List2<Class<?>>, ValueConvertor<?, ?>> CACHE = new LRUMap<>(512, 32);
    @GuardedBy("self")
    private static Map<List2<Class<?>>, ValueConvertor<?, ?>> LENIENT_CACHE = new LRUMap<>(512, 32);
    static {
        // common
        LENIENT_CONVERTORS = new ArrayList<>();
        STRICT_CONVERTORS = new ArrayList<>();
        LENIENT_CONVERTORS.add(new DateTSConvertor());
        LENIENT_CONVERTORS.add(new DateToSQLConvertor());
        LENIENT_CONVERTORS.add(new DateToTimeConvertor());
        LENIENT_CONVERTORS.add(NumberConvertor.INT_TO_LONG);
        LENIENT_CONVERTORS.add(NumberConvertor.SHORT_TO_INT);
        LENIENT_CONVERTORS.add(JavaTimeSQLConvertors.LOCAL_DATE);
        LENIENT_CONVERTORS.add(JavaTimeSQLConvertors.LOCAL_TIME);
        LENIENT_CONVERTORS.add(JavaTimeSQLConvertors.LOCAL_DATETIME);
        STRICT_CONVERTORS.addAll(LENIENT_CONVERTORS);

        STRICT_CONVERTORS.add(StringClobConvertor.INSTANCE);
        STRICT_CONVERTORS.add(NumberConvertor.INT_TO_BIGDECIMAL_EXACT);

        LENIENT_CONVERTORS.add(StringClobConvertor.INSTANCE_LENIENT);
    }

    public static final <T, U> U convert(final T o, final Class<U> c2, final boolean lenient) {
        if (c2.isInstance(o))
            return c2.cast(o);
        @SuppressWarnings("unchecked")
        final ValueConvertor<T, U> conv = (ValueConvertor<T, U>) find(o.getClass(), c2, lenient);
        if (conv == null)
            throw new IllegalArgumentException("No convertor to " + c2 + " from " + o);
        return conv.convert(o);
    }

    public static final <T, U> ValueConvertor<T, U> find(Class<T> c1, Class<U> c2) {
        return find(c1, c2, false);
    }

    public static final <T, U> ValueConvertor<T, U> find(Class<T> c1, Class<U> c2, final boolean lenient) {
        final ValueConvertor<?, ?> res;
        if (c1 == c2) {
            res = getIdentityConvertor();
        } else {
            final List2<Class<?>> key = new List2<>(c1, c2);
            final Map<List2<Class<?>>, ValueConvertor<?, ?>> c = lenient ? LENIENT_CACHE : CACHE;
            synchronized (c) {
                final ValueConvertor<?, ?> inCache = c.get(key);
                if (inCache == MISSING_CONV) {
                    return null;
                } else if (inCache != null) {
                    res = inCache;
                } else {
                    res = create(c1, c2, lenient);
                    c.put(key, res == null ? MISSING_CONV : res);
                }
            }
        }
        @SuppressWarnings("unchecked")
        final ValueConvertor<T, U> casted = (ValueConvertor<T, U>) res;
        return casted;
    }

    private static final ValueConvertor<?, ?> create(Class<?> c1, Class<?> c2, final boolean lenient) {
        assert c1 != c2;

        for (final ValueConvertor<?, ?> vc : (lenient ? LENIENT_CONVERTORS : STRICT_CONVERTORS)) {
            final List<Class<?>> args = ReflectUtils.getTypeArguments(vc, ValueConvertor.class);
            if (args.size() != 2)
                throw new IllegalStateException(vc + " don't specify type arguments");
            if (args.get(0).equals(c1) && args.get(1).equals(c2)) {
                return vc;
            } else if (args.get(0).equals(c2) && args.get(1).equals(c1)) {
                return ReverseConvertor.create(vc);
            }
        }
        if (Number.class.isAssignableFrom(c1) && Number.class.isAssignableFrom(c2))
            return NumberConvertor.create(c1.asSubclass(Number.class), c2.asSubclass(Number.class), !lenient);

        return null;
    }
}
