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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jopendocument.util.FileUtils;
import org.jopendocument.util.PropertiesUtils;
import org.jopendocument.util.Zip;
import org.jopendocument.util.prog.VMLauncher;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HelperTest {

    @TempDir
    public File tmpFolder;

    @BeforeEach
    public void setUp() {
        Helper.register();
    }

    @AfterEach
    public void tearDown() {
        Helper.unregister();
    }

    @Test
    public void test() throws Exception {
        final File dir = this.tmpFolder;
        final File outputDir = new File(dir, "classes");
        FileUtils.mkdir_p(outputDir);

        // Create the class that will be in the outer jar, reading the inner jar
        final JavaSourceFromString helloFile = new JavaSourceFromString("Hello", "public class Hello { static public void main(String[] args) throws Exception {"
                //
                + "org.jopendocument.util.protocol.Helper.register();\n"
                //
                + "java.net.URL msgURL = org.jopendocument.util.protocol.Helper.intoJar(Hello.class.getResource(\"rsrc.jar\"), \"message.txt\");\n"
                //
                + "final String message;\n"
                //
                + "try (final java.io.InputStream ins = msgURL.openConnection().getInputStream()) {\n"
                //
                + "            message = new String(org.jopendocument.util.StreamUtils.read(ins), java.nio.charset.StandardCharsets.UTF_8);\n" +
                //
                "}\n"
                //
                + "System.out.println(\"URL=\" + msgURL);\n"
                //
                + "System.out.println(\"fromJarTxt=\" + message);\n"
                //
                + "} } ");
        assertTrue(JavaSourceFromString.compile(outputDir, helloFile));
        final File classFile = new File(outputDir, helloFile.getClassFile());

        // Create the inner jar
        final File resourceJarFile = new File(dir, "rsrc.jar");
        final String message = "Hello world !";
        try (final Zip out = Zip.createJar(new FileOutputStream(resourceJarFile))) {
            out.zip("message.txt", message.getBytes(StandardCharsets.UTF_8), true);
        }

        // Create the outer jar
        final File jarFile = new File(dir, "hello.jar");
        try (final Zip out = Zip.createJar(new FileOutputStream(jarFile))) {
            for (final File f : Arrays.asList(classFile, resourceJarFile)) {
                out.zip(f);
            }
        }

        final File outputPropsFile = new File(dir, "testProgOutput.properties");
        final Process proc = new VMLauncher() {
            @Override
            protected String getClassPath() {
                return jarFile.getAbsolutePath() + File.pathSeparatorChar + super.getClassPath();
            }

            @Override
            protected Redirect getStreamRedirect() {
                return Redirect.to(outputPropsFile);
            }
        }.launch("Hello");
        assertEquals(0, proc.waitFor());
        final Properties outputProps = PropertiesUtils.createFromFile(outputPropsFile);
        // we did use the jarjar protocol
        assertTrue(outputProps.getProperty("URL").startsWith("jar:jarjar:file"));
        // and the content was correctly read
        assertEquals(message, outputProps.getProperty("fromJarTxt"));
    }

}
