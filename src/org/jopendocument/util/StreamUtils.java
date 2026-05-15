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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class StreamUtils {

    public static final OutputStream NULL_OS = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // ignore
        }

        public void write(byte b[], int off, int len) throws IOException {
            if (b == null)
                throw new NullPointerException();
            // ignore
        }
    };

    public static final File NULL_FILE = new File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");

    /**
     * Verbatim copy an entry from input to output stream.
     * 
     * @param in the source.
     * @param out the destination.
     * @throws IOException if an error occurs while reading or writing.
     * @throws RTInterruptedException if interrupted (can only be checked between
     *         {@link InputStream#read(byte[], int, int)} reads).
     */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        // TODO use in.transferTo(out) in Java 9
        copy(in, out, 512 * 1024);
    }

    public static void copy(InputStream in, OutputStream out, final int bufferSize) throws IOException {
        copy(in, out, bufferSize, -1);
    }

    public static long copy(InputStream in, OutputStream out, final int bufferSize, final long length) throws IOException {
        if (bufferSize < 1)
            throw new IllegalArgumentException("Buffer size too small : " + bufferSize);
        final byte[] buffer = new byte[bufferSize];
        long totalCount = 0;
        final boolean copyAll = length < 0;
        while (copyAll || totalCount < length) {
            final long toRead = copyAll ? buffer.length : Math.min(length - totalCount, buffer.length);
            // since buffer.length is an int
            assert 0 < toRead && toRead <= Integer.MAX_VALUE;
            if (Thread.interrupted())
                throw new RTInterruptedException();
            final int count = in.read(buffer, 0, (int) toRead);
            if (count <= 0) {
                // like Files.copy(InputStream, OutputStream), stop if reading 0 bytes
                if (count == 0)
                    Log.get().log(Level.WARNING, "", new IllegalStateException("read() returned 0 for " + in));
                break;
            }
            totalCount += count;
            // if interrupted while blocked in read(), then we don't want to use the data read after
            // the interrupt.
            if (Thread.interrupted())
                throw new RTInterruptedException();
            out.write(buffer, 0, count);
        }
        // < if end of stream
        assert copyAll || totalCount <= length;
        return totalCount;
    }

    public static void copy(InputStream ins, File out) throws IOException {
        Files.copy(ins, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Read until the end of the stream is reached. NOTE : since this method didn't create the
     * stream, it doesn't close it.
     * 
     * @param ins the stream to read from.
     * @return the bytes.
     * @throws IOException if an error occurs.
     */
    public static byte[] read(final InputStream ins) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        copy(ins, out);
        return out.toByteArray();
    }

    public static void writeln(final String s, final OutputStream out) throws IOException {
        write(s + "\n", out);
    }

    public static void write(final String s, final OutputStream out) throws IOException {
        write(s, out, StringUtils.UTF8);
    }

    public static void write(final String s, final OutputStream out, Charset charset) throws IOException {
        out.write(s.getBytes(charset));
    }

    /**
     * Wrap the output stream into a writer, and write the XML declaration.
     * 
     * @param outs an output stream.
     * @return a writer with the same encoding as the XML.
     * @throws IOException if an error occurs.
     */
    public static BufferedWriter createXMLWriter(OutputStream outs) throws IOException {
        return new BufferedWriter(createXMLUnbufferedWriter(outs));
    }

    public static Writer createXMLUnbufferedWriter(OutputStream outs) throws IOException {
        // see http://www.w3.org/TR/REC-xml/#sec-guessing
        // don't use UTF-8 BOM as Java does not support it :
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4508058
        final Writer res = new OutputStreamWriter(outs, StringUtils.UTF8);
        res.write("<?xml version='1.0' encoding='UTF-8' ?>\n");
        return res;
    }
}
