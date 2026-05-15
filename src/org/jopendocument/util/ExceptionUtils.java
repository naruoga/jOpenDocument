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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Utilitaires pour les exceptions.
 * 
 * @author Sylvain CUAZ 25 nov. 2004
 */
public class ExceptionUtils {
    /** Static only. */
    private ExceptionUtils() {
        super();
    }

    /**
     * Crée une exception avec message et cause.
     * 
     * @param <T> le type d'exception à créer.
     * @param exnClass la classe de l'exception à créer, eg IOException.class.
     * @param msg le message.
     * @param cause la cause.
     * @return une exception initialisée.
     */
    static public <T extends Exception> T createExn(Class<T> exnClass, String msg, Throwable cause) {
        T instance = null;
        try {
            Constructor<T> ctor = exnClass.getConstructor(new Class[] { String.class });
            instance = ctor.newInstance(new Object[] { msg });
        } catch (Exception exn) {
            throw new IllegalArgumentException(exnClass + " has no working String constructor");
        }
        instance.initCause(cause);
        return instance;
    }

    /*
     * return an exception so that the caller can "throw ExceptionUtils.throwExn(exn,
     * SQLException.class, RuntimeException.class);" otherwise the compiler will complain that the
     * method doesn't return. Obviously the return will never be used.
     */
    static public <T extends Throwable, X extends T, Y extends T> X throwExn(T toThrow, Class<X> classX, Class<Y> classY) throws X, Y {
        if (classX.isInstance(toThrow))
            throw classX.cast(toThrow);
        else
            throw classY.cast(toThrow);
    }

    /**
     * Crée une RuntimeException.
     * 
     * @param <T> le type d'exception à créer.
     * @param exnClass la classe de l'exception à créer, eg IllegalArgumentException.class.
     * @param msg le message.
     * @param cause la cause.
     * @return une RuntimeException initialisée.
     * @throws IllegalArgumentException if exnClass is not Runtime.
     * @see #createExn(Class, String, Throwable)
     * @deprecated use {@link #createExn(Class, String, Throwable)}
     */
    static public <T extends RuntimeException> T createRTExn(Class<T> exnClass, String msg, Throwable cause) {
        if (!RuntimeException.class.isAssignableFrom(exnClass))
            throw new IllegalArgumentException(exnClass + " is not a Runtime exception");

        return createExn(exnClass, msg, cause);
    }

    static public String getStackTrace(Throwable cause) {
        final StringWriter res = new StringWriter(8192);
        final PrintWriter pw = new PrintWriter(res);
        try {
            cause.printStackTrace(pw);
        } finally {
            pw.close();
        }
        if (pw.checkError())
            Log.get().warning("Error while writing " + cause);
        return res.toString();
    }

    static public void requireEquals(final BigDecimal a, final BigDecimal b, final String msg) {
        require((bd1, bd2) -> bd1.compareTo(bd2) == 0, a, b, msg);
    }

    static public void requireEquals(final Object a, final Object b, final String msg) {
        require(Objects::equals, a, b, msg);
    }

    static public <T> void require(final BiPredicate<T, T> pred, final T a, final T b, final String msg) {
        if (!pred.test(a, b))
            throw new IllegalArgumentException(msg + ", expected " + a + " but got " + b);
    }

    static public void requireSame(final Object a, final Object b, final String msg) {
        if (a != b)
            throw new IllegalArgumentException(msg + ", expected same reference to " + a + " but got " + b);
    }

    static public void requireEquals(final int a, final int b, final String msg) {
        if (a != b)
            throw new IllegalArgumentException(msg + ", expected " + a + " but got " + b);
    }
}