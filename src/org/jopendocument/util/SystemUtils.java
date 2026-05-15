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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class SystemUtils {

    /**
     * Wrap a system property and view it as a list.
     * 
     * @author Sylvain CUAZ
     */
    public static final class PropertyList {
        private final String name;
        private final Pattern p;

        /**
         * Create a new instance.
         * 
         * @param name name of the property, e.g. "java.protocol.handler.pkgs".
         * @param sep the separator used, e.g. "|".
         */
        public PropertyList(final String name, final String sep) {
            this.name = name;
            this.p = Pattern.compile(sep, Pattern.LITERAL);
        }

        public final String getName() {
            return this.name;
        }

        private final String getSeparator() {
            return this.p.pattern();
        }

        public final String getValue() {
            return System.getProperty(this.name);
        }

        public final List<String> getValues() {
            return getList(getValue());
        }

        private final List<String> getList(final String current) {
            if (current == null)
                return null;
            else if (current.length() == 0)
                return Collections.emptyList();
            else {
                return Arrays.asList(this.p.split(current));
            }
        }

        /**
         * Adds a value to the system property list if not already present.
         * 
         * @param value the value to add, e.g. "sun.net.www.protocol".
         * @return <code>true</code> if the property was modified.
         */
        public final boolean add(final String value) {
            return this.add(value, true);
        }

        public final boolean add(final String value, boolean append) {
            if (value == null)
                throw new NullPointerException("Null value");

            final String current = getValue();
            final List<String> l = getList(current);
            final String newVal;
            if (l == null || l.size() == 0)
                newVal = value;
            else if (l.contains(value))
                newVal = null;
            else if (append)
                newVal = current + this.getSeparator() + value;
            else
                newVal = value + this.getSeparator() + current;

            if (newVal != null) {
                System.setProperty(this.name, newVal);
                return true;
            } else
                return false;
        }

        public final boolean remove(final String value) {
            return this.remove(value, true);
        }

        public final boolean remove(final String value, final boolean unsetIfEmpty) {
            if (value == null)
                throw new NullPointerException("Null value");
            final List<String> l = getValues();
            if (l == null || l.size() == 0 || !l.contains(value))
                return false;

            final List<String> newList = new ArrayList<>(l.size() - 1);
            for (final String item : l) {
                if (!item.equals(value))
                    newList.add(item);
            }
            if (unsetIfEmpty && newList.isEmpty())
                System.clearProperty(this.name);
            else
                System.setProperty(this.name, CollectionUtils.join(newList, this.getSeparator()));
            return true;
        }
    }

    static public final <T extends Enum<T>> T getEnumFromProperty(final String propName, final Class<T> clazz, final T def) {
        final String prop = System.getProperty(propName);
        if (prop == null)
            return def;
        return Enum.valueOf(clazz, propName);
    }

    static public final int getIntProperty(final String propName, final int def) {
        return getProperty(propName, Integer::parseInt, def);
    }

    static public final <T> T getProperty(final String propName, final Function<String, T> func, final T def) {
        final String prop = System.getProperty(propName);
        if (prop == null)
            return def;
        return func.apply(prop);
    }
}
