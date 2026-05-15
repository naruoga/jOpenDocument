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

package org.jopendocument.util.protocol;

import org.jopendocument.util.SystemUtils.PropertyList;
import org.jopendocument.util.protocol.jarjar.Handler;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class Helper {

    static private final PropertyList PL = new PropertyList("java.protocol.handler.pkgs", "|");

    static final public void register() {
        // Works even if setURLStreamHandlerFactory() is called (as long as the factory returns null
        // for our protocols)
        // On Java 11, there's also URLStreamHandlerProvider
        PL.add(Helper.class.getPackage().getName());
    }

    static final public void unregister() {
        PL.remove(Helper.class.getPackage().getName());
    }

    /**
     * Set the {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory) factory} to add our
     * protocols. This is needed for example in web start when one of our url is embedded into a
     * library supplied one. E.g. "jar:jarjar:file:/C:/mylibs/Outer.jar^/Inner.jar!/" will cause the
     * jar Handler to try to create a jarjar URL but its classloader cannot access our classes
     * (loaded by JNLPClassLoader).
     */
    static final public void setURLStreamHandlerFactory() {
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if (protocol.equals("jarjar"))
                    return new Handler();
                else
                    return null;
            }
        });
    }

    /**
     * Return a jar URL to the root of a jar file. Needed since the JRE cannot read files inside a
     * jar inside a jar.
     * 
     * @param u the URL to wrap, e.g. "jar:file:/C:/mylibs/Outer.jar!/Inner.jar".
     * @return the wrapped URL, e.g. "jar:jarjar:file:/C:/mylibs/Outer.jar^/Inner.jar!/".
     */
    public static final URL intoJar(final URL u) {
        return intoJar(u, "");
    }

    public static final URL intoJar(final URL u, final String s) {
        if (!u.getPath().endsWith(".jar"))
            throw new IllegalArgumentException("Doesn't end with .jar :" + u);

        final URL res;
        // if it's a jar inside another jar
        if ("jar".equals(u.getProtocol())) {
            try {
                res = new URL("jar:jar" + u.toExternalForm().replace('!', '^') + "!/" + s);
            } catch (MalformedURLException e) {
                // shouldn't happen since we modify a valid URL
                throw new IllegalStateException("Couldn't transform to jarjar " + u, e);
            }
        } else {
            try {
                res = new URL("jar:" + u.toExternalForm() + "!/" + s);
            } catch (MalformedURLException e) {
                // shouldn't happen since constructed from a valid URL
                throw new IllegalStateException("Couldn't transform to jar URL " + u, e);
            }
        }
        return res;
    }

}
