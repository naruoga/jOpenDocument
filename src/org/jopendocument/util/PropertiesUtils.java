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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class PropertiesUtils {

    public static final Properties createFromMap(final Map<String, String> map) {
        return loadFromMap(new Properties(), map);
    }

    public static final Properties loadFromMap(final Properties res, final Map<String, String> map) {
        for (final Entry<String, String> e : map.entrySet()) {
            final String key = e.getKey();
            if (key == null)
                throw new NullPointerException("Null key : " + e);
            final String val = e.getValue();
            // Properties doesn't support nulls
            if (val != null)
                res.setProperty(key, val);
        }
        return res;
    }

    public static final Map<String, String> toMap(final Properties props) {
        final Map<String, String> res = new HashMap<>();
        for (final String key : props.stringPropertyNames()) {
            final String value = props.getProperty(key);
            assert value != null;
            res.put(key, value);
        }
        return res;
    }

    public static final Properties createFromFile(final File f) throws IOException {
        try (final InputStream stream = new BufferedInputStream(new FileInputStream(f))) {
            return create(stream);
        }
    }

    public static final Properties createFromResource(final Class<?> ctxt, final String rsrc) throws IOException {
        try (final InputStream stream = ctxt.getResourceAsStream(rsrc)) {
            return create(stream);
        }
    }

    public static final Properties create(final InputStream stream) throws IOException {
        if (stream != null) {
            final Properties res = new Properties();
            res.load(stream);
            return res;
        } else {
            return null;
        }
    }

    public static final void load(final Properties props, final Properties toLoad) {
        for (final String key : toLoad.stringPropertyNames()) {
            final String value = toLoad.getProperty(key);
            assert value != null;
            props.setProperty(key, value);
        }
    }
}
