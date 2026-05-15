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

import org.jopendocument.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * From {@link JavaCompiler} javadoc.
 */
public class JavaSourceFromString extends SimpleJavaFileObject {

    static public boolean compile(final File outputDir, final JavaFileObject... classes) throws IOException {
        FileUtils.mkdir_p(outputDir);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDir));
            return compiler.getTask(null, fileManager, null, null, null, Arrays.asList(classes)).call().booleanValue();
        }
    }

    /**
     * The source code of this "file".
     */
    private final String code;

    /**
     * Constructs a new JavaSourceFromString.
     * 
     * @param name the name of the compilation unit represented by this file object
     * @param code the source code for the compilation unit represented by this file object
     */
    public JavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    public final String getClassFile() {
        // /pkg/Inner.java
        final String name = this.getName();
        // pkg/Inner.class
        return name.substring(1, name.length() - Kind.SOURCE.extension.length()) + Kind.CLASS.extension;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return this.code;
    }
}