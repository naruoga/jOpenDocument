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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

public class ValueConvertorFactoryTest {
    @Test
    public void testFind() {
        testFind(true);
        testFind(false);
    }

    private void testFind(final boolean lenient) {
        assertNull(ValueConvertorFactory.find(List.class, Currency.class, lenient));
        // test null caching
        assertNull(ValueConvertorFactory.find(List.class, Currency.class, lenient));

        final ValueConvertor<Double, Integer> double2int = ValueConvertorFactory.find(Double.class, Integer.class, lenient);
        // test cache
        assertSame(double2int, ValueConvertorFactory.find(Double.class, Integer.class, lenient));
        failsIfStrict(lenient, Integer.valueOf(3), () -> double2int.convert(3.0d));
        assertEquals(Double.valueOf(3.0d), double2int.unconvert(3));

        final ValueConvertor<BigDecimal, Integer> dec2int = ValueConvertorFactory.find(BigDecimal.class, Integer.class, lenient);
        failsIfStrict(lenient, Integer.valueOf(3), () -> dec2int.convert(new BigDecimal("3.2")));
        assertEquals(Integer.valueOf(3), dec2int.convert(new BigDecimal("3.0")));
        assertEquals(new BigDecimal("3"), dec2int.unconvert(3));
    }

    private <T> void failsIfStrict(final boolean lenient, final T expected, final Supplier<T> test) {
        try {
            assertEquals(expected, test.get());
            if (!lenient)
                fail("Should have thrown");
        } catch (ArithmeticException e) {
            if (lenient)
                throw e;
        }
    }

    @Test
    public void testConvert() {
        assertEquals(Long.valueOf(3), ValueConvertorFactory.convert(3, Long.class, false));
        assertEquals(Long.valueOf(3), ValueConvertorFactory.convert(3.2, Long.class, true));
        try {
            ValueConvertorFactory.convert(3.2, List.class, true);
            fail("Shouldn't convert");
        } catch (IllegalArgumentException e) {
            // OK
        }
    }
}
