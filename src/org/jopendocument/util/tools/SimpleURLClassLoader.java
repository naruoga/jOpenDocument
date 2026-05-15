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

package org.jopendocument.util.tools;

import org.jopendocument.util.Log;
import org.jopendocument.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Class loader which supports any URL. {@link URLClassLoader} only supports directories and jar
 * files, but is more optimized (e.g. for finding which URL contains a resource)
 * 
 * @author sylvain
 */
public class SimpleURLClassLoader extends SecureClassLoader {

    static public final URL toURL(final File f) {
        try {
            return f.toURI().toURL();
        } catch (MalformedURLException e) {
            // shouldn't happen since constructed from a file
            throw new IllegalStateException("Couldn't transform file to URL " + f, e);
        }
    }

    static public class URLCollector {
        private final List<URL> urls = new ArrayList<>();

        public final List<URL> copyToUnmodifiableList() {
            return Collections.unmodifiableList(new ArrayList<>(this.urls));
        }

        public final URLCollector add(final URL url) {
            this.urls.add(url);
            return this;
        }

        public final URLCollector addDirectory(final File dir) {
            if (!dir.isDirectory())
                throw new IllegalArgumentException("Not a directory : " + dir);
            return this.add(toURL(dir));
        }

        public final URLCollector addJar(final File jar) {
            if (!jar.isFile())
                throw new IllegalArgumentException("Not a file : " + jar);
            return this.addJar(toURL(jar));
        }

        public final URLCollector addJar(final URL u) {
            return this.add(org.jopendocument.util.protocol.Helper.intoJar(u));
        }

        public final URLCollector addJars(final Iterable<URL> jars) {
            for (final URL jar : jars)
                this.addJar(jar);
            return this;
        }
    }

    // from ResourceBundle.Control
    static public final String toResourceName(String bundleName, String suffix) {
        StringBuilder sb = new StringBuilder(bundleName.length() + 1 + suffix.length());
        sb.append(bundleName.replace('.', '/')).append('.').append(suffix);
        return sb.toString();
    }

    static public final String toClassFile(String bundleName) {
        return toResourceName(bundleName, "class");
    }

    private final List<URL> urls;

    public SimpleURLClassLoader(final URLCollector urls) {
        super();
        this.urls = urls.copyToUnmodifiableList();
    }

    public SimpleURLClassLoader(final URLCollector urls, final ClassLoader parent) {
        super(parent);
        this.urls = urls.copyToUnmodifiableList();
    }

    protected URL createURL(final URL url, final String spec) throws MalformedURLException {
        return new URL(url, spec);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (final URL baseURL : this.urls) {
            try {
                final URL url = createURL(baseURL, toClassFile(name));
                try (final InputStream ins = url.openConnection().getInputStream()) {
                    try {
                        final byte[] bytes = StreamUtils.read(ins);
                        return this.defineClass(name, bytes, 0, bytes.length, new CodeSource(url, (CodeSigner[]) null));
                    } catch (IOException readExn) {
                        // same behaviour as URLClassLoader.findClass()
                        throw new ClassNotFoundException(name, readExn);
                    }
                }
            } catch (IOException connectExn) {
                Log.get().log(Level.FINE, connectExn, () -> "Couldn't connect to " + baseURL + " for " + name);
                // next
            }
        }
        return super.findClass(name);
    }

    @Override
    protected URL findResource(String name) {
        for (final URL baseURL : this.urls) {
            try {
                final URL url = createURL(baseURL, name);
                boolean exists = false;
                if (url.getProtocol().equals("file")) {
                    exists = new File(url.toURI()).exists();
                } else {
                    // From sun.misc.URLClassPath.Loader
                    URLConnection uc = url.openConnection();
                    if (uc instanceof HttpURLConnection) {
                        HttpURLConnection hconn = (HttpURLConnection) uc;
                        hconn.setRequestMethod("HEAD");
                        exists = hconn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST;
                    } else {
                        // our best guess for the other cases
                        uc.setUseCaches(false);
                        try (final InputStream ins = uc.getInputStream()) {
                            exists = true;
                        } catch (IOException e) {
                            exists = false;
                        }
                    }
                }
                if (exists)
                    return url;
            } catch (Exception e) {
                // next
            }
        }
        return super.findResource(name);
    }
}
