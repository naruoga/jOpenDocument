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

import org.jopendocument.util.CollectionMap2.Mode;
import org.jopendocument.util.DesktopEnvironment.Gnome;
import org.jopendocument.util.OSFamily.Unix;
import org.jopendocument.util.StringUtils.Escaper;
import org.jopendocument.util.cc.ExnTransformer;
import org.jopendocument.util.cc.IClosure;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class FileUtils {

    public static void main(String[] args) throws Exception {
        final String cmd = args[0];
        if ("browseFile".equals(cmd))
            browseFile(new File(args[1]));
        else if ("browse".equals(cmd))
            browse(new URI(args[1]));
        else
            System.err.println("Unkown command : " + cmd);
    }

    private FileUtils() {
        // all static
    }

    public static void browseFile(final File f) throws IOException {
        browse(null, Objects.requireNonNull(f));
    }

    private static void browse(URI uri, final File f) throws IOException {
        assert (uri == null) != (f == null);
        boolean handled = false;
        IOException exn = null;
        final Desktop.Action action = Desktop.Action.BROWSE;
        if (isDesktopDesirable(action) && Desktop.isDesktopSupported()) {
            final Desktop d = Desktop.getDesktop();
            if (d.isSupported(action)) {
                if (uri == null)
                    uri = f.getCanonicalFile().toURI();
                if (!uri.getScheme().equals("file") && DesktopEnvironment.getDE() instanceof Gnome) {
                    ProcessBuilder pb = null;
                    final String version = DesktopEnvironment.getDE().getVersion();
                    // Ubuntu 12.04, 14.04, 16.04
                    if (version.startsWith("3.4.") || version.startsWith("3.10.") || version.startsWith("3.18.")) {
                        pb = new ProcessBuilder("gvfs-mount", uri.toASCIIString());
                        // Ubuntu 18.04
                    } else if (version.startsWith("3.28")) {
                        // gio/gio-tool-mount.c#mount() calls g_file_mount_enclosing_volume.
                        // TODO find out how glib computes "the volume that contains the file
                        // location". E.g why does mount "davs://example.com/webdav/dir/dir" mounts
                        // "davs://example.com/webdav/".
                        // Return 0 if not yet mounted, 2 if it was already mounted.
                        pb = new ProcessBuilder("gio", "mount", uri.toASCIIString());
                    }
                    if (pb != null) {
                        try {
                            startDiscardingOutput(pb).waitFor();
                        } catch (InterruptedException e) {
                            throw new RTInterruptedException("Interrupted while waiting on mount for " + uri, e);
                        }
                    }
                }
                try {
                    d.browse(uri);
                    handled = true;
                } catch (IOException e) {
                    // On Ubuntu, we sometimes get spurious errors, so retry
                    exn = e;
                }
            }
        }
        if (!handled) {
            try {
                // if the caller passed a file use it instead of our converted URI
                if (f != null)
                    openNative(f);
                else
                    openNative(uri);
            } catch (IOException | RuntimeException e) {
                if (exn != null) {
                    exn.addSuppressed(e);
                    throw exn;
                } else {
                    throw e;
                }
            }
        }
    }

    public static boolean isDesktopDesirable(Desktop.Action action) {
        // apparently the JRE just checks if gnome libs are available (e.g. open Nautilus in XFCE)
        return !(action == Desktop.Action.BROWSE && OSFamily.getInstance() == OSFamily.Linux && !(DesktopEnvironment.getDE() instanceof Gnome));
    }

    public static void browse(URI uri) throws IOException {
        browse(Objects.requireNonNull(uri), null);
    }

    public static void openFile(File f) throws IOException {
        if (!f.exists()) {
            throw new FileNotFoundException(f.getAbsolutePath() + " not found");
        }
        if (Desktop.isDesktopSupported()) {
            Desktop d = Desktop.getDesktop();
            if (d.isSupported(Desktop.Action.OPEN)) {
                d.open(f.getCanonicalFile());
            } else {
                openNative(f);
            }
        } else {
            openNative(f);
        }
    }

    // immutable, getAbsoluteFile() required otherwise list() returns null
    static private final File WD = new File("").getAbsoluteFile();

    static public final File getWD() {
        return WD;
    }

    /**
     * All the files (see {@link File#isFile()}) contained in the passed dir.
     * 
     * @param dir the root directory to search.
     * @return a List of String.
     */
    public static List<String> listR(File dir) {
        return listR(dir, REGULAR_FILE_FILTER);
    }

    public static List<String> listR(File dir, FileFilter ff) {
        return listR_rec(dir, ff, ".");
    }

    private static List<String> listR_rec(File dir, FileFilter ff, String prefix) {
        if (!dir.isDirectory())
            return null;

        final List<String> res = new ArrayList<String>();
        final File[] children = dir.listFiles();
        for (int i = 0; i < children.length; i++) {
            final String newPrefix = prefix + "/" + children[i].getName();
            if (ff == null || ff.accept(children[i])) {
                res.add(newPrefix);
            }
            if (children[i].isDirectory()) {
                res.addAll(listR_rec(children[i], ff, newPrefix));
            }
        }
        return res;
    }

    public static void walk(File dir, IClosure<File> c) {
        walk(dir, c, RecursionType.BREADTH_FIRST);
    }

    public static void walk(File dir, IClosure<File> c, RecursionType type) {
        if (type == RecursionType.BREADTH_FIRST)
            c.executeChecked(dir);
        if (dir.isDirectory()) {
            for (final File child : dir.listFiles()) {
                walk(child, c, type);
            }
        }
        if (type == RecursionType.DEPTH_FIRST)
            c.executeChecked(dir);
    }

    public static final List<File> list(File root, final int depth) {
        return list(root, depth, null);
    }

    /**
     * Finds all files at the specified depth below <code>root</code>.
     * 
     * @param root the base directory
     * @param depth the depth of the returned files.
     * @param ff a filter, can be <code>null</code>.
     * @return a list of files <code>depth</code> levels beneath <code>root</code>.
     */
    public static final List<File> list(File root, final int depth, final FileFilter ff) {
        return list(root, depth, depth, ff);
    }

    public static final List<File> list(final File root, final int minDepth, final int maxDepth, final FileFilter ff) {
        return list(root, minDepth, maxDepth, ff, false);
    }

    public static final List<File> list(final File root, final int minDepth, final int maxDepth, final FileFilter ff, final boolean sort) {
        if (minDepth > maxDepth)
            throw new IllegalArgumentException(minDepth + " > " + maxDepth);
        if (maxDepth < 0)
            throw new IllegalArgumentException(maxDepth + " < 0");
        if (!root.exists())
            return Collections.<File> emptyList();

        final File currentFile = accept(ff, minDepth, maxDepth, root, 0) ? root : null;
        if (maxDepth == 0) {
            return currentFile == null ? Collections.<File> emptyList() : Collections.singletonList(currentFile);
        } else {
            final List<File> res = new ArrayList<File>();
            final File[] children = root.listFiles();
            if (children == null)
                throw new IllegalStateException("cannot list " + root);
            if (sort)
                Arrays.sort(children);
            for (final File child : children) {
                if (maxDepth > 1 && child.isDirectory()) {
                    res.addAll(list(child, minDepth - 1, maxDepth - 1, ff, sort));
                } else if (accept(ff, minDepth, maxDepth, child, 1)) {
                    res.add(child);
                }
            }
            if (currentFile != null)
                res.add(currentFile);
            return res;
        }
    }

    private static final boolean accept(final FileFilter ff, final int minDepth, final int maxDepth, final File f, final int depth) {
        return minDepth <= depth && depth <= maxDepth && (ff == null || ff.accept(f));
    }

    /**
     * Returns the relative path from one file to another in the same filesystem tree. Files are not
     * required to exist, see {@link File#getCanonicalPath()}.
     * 
     * @param fromDir the starting directory, eg /a/b/.
     * @param to the file to get to, eg /a/x/y.txt.
     * @return the relative path, eg "../x/y.txt".
     * @throws IOException if an error occurs while canonicalizing the files.
     * @throws IllegalArgumentException if fromDir exists and is not directory.
     * @see {@link Path#relativize(Path)}
     */
    public static final String relative(File fromDir, File to) throws IOException {
        if (fromDir.exists() && !fromDir.isDirectory())
            throw new IllegalArgumentException(fromDir + " is not a directory");

        final File fromF = fromDir.getCanonicalFile();
        final File toF = to.getCanonicalFile();
        final List<File> toPath = getAncestors(toF);
        final List<File> fromPath = getAncestors(fromF);

        // no common ancestor (for example on Windows on 2 different letters)
        if (!toPath.get(0).equals(fromPath.get(0))) {
            // already canonical
            return toF.getPath();
        }

        int commonIndex = Math.min(toPath.size(), fromPath.size()) - 1;
        boolean found = false;
        while (commonIndex >= 0 && !found) {
            found = fromPath.get(commonIndex).equals(toPath.get(commonIndex));
            if (!found)
                commonIndex--;
        }

        // on remonte jusqu'à l'ancêtre commun
        final List<String> complete = new ArrayList<String>(Collections.nCopies(fromPath.size() - 1 - commonIndex, ".."));
        if (complete.isEmpty())
            complete.add(".");
        // puis on descend vers 'to'
        for (File f : toPath.subList(commonIndex + 1, toPath.size())) {
            complete.add(f.getName());
        }

        return CollectionUtils.join(complete, File.separator);
    }

    // return each ancestor of f (including itself)
    // eg [/, /folder, /folder/dir] for /folder/dir
    public final static List<File> getAncestors(File f) {
        final List<File> path = new ArrayList<File>();
        File currentF = f;
        while (currentF != null) {
            path.add(0, currentF);
            currentF = currentF.getParentFile();
        }
        return path;
    }

    public final static File addSuffix(File f, String suffix) {
        return new File(f.getParentFile(), f.getName() + suffix);
    }

    /**
     * Prepend a string to a suffix.
     * 
     * @param f the file, e.g. "sample.xml".
     * @param toInsert the string to insert in the filename, e.g. "-sql".
     * @param suffix the suffix of <code>f</code>, e.g. ".xml".
     * @return a new file with <code>toInsert</code> prepended to <code>suffix</code>, e.g.
     *         "sample-sql.xml".
     */
    public final static File prependSuffix(File f, String toInsert, String suffix) {
        return new File(f.getParentFile(), removeSuffix(f.getName(), suffix) + toInsert + suffix);
    }

    /**
     * Prepend a string to a suffix.
     * 
     * @param f the file, e.g. "sample.xml".
     * @param toInsert the string to insert in the filename, e.g. "-sql".
     * @param suffix the suffix of <code>f</code>, e.g. ".xml".
     * @return a new file with <code>toInsert</code> prepended to <code>suffix</code>, e.g.
     *         "sample-sql.xml".
     */
    public final static Path prependSuffix(Path f, String toInsert, String suffix) {
        return f.resolveSibling(removeSuffix(f.getFileName().toString(), suffix) + toInsert + suffix);
    }

    public final static String removeSuffix(String name, String suffix) {
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /**
     * Rename a file if necessary by finding a free name. The tested names are
     * <code>name + "_" + i + suffix</code>.
     * 
     * @param parent the directory.
     * @param name the base name of the file.
     * @param suffix the suffix of the file, e.g. ".ods".
     * @return <code>new File(parent, name + suffix)</code> (always non existing) and the new file,
     *         (or <code>null</code> if no file was moved).
     */
    public final static File[] mvOut(final File parent, final String name, final String suffix) {
        final File fDest = new File(parent, name + suffix);
        final File renamed;
        if (fDest.exists()) {
            int i = 0;
            File free = fDest;
            while (free.exists()) {
                free = new File(parent, name + "_" + i + suffix);
                i++;
            }
            assert !fDest.equals(free);
            if (!fDest.renameTo(free))
                throw new IllegalStateException("Couldn't rename " + fDest + " to " + free);
            renamed = free;
        } else {
            renamed = null;
        }
        assert !fDest.exists();
        return new File[] { fDest, renamed };
    }

    // ** shell

    /**
     * Behave like the 'mv' unix utility, ie handle cross filesystems mv and <code>dest</code> being
     * a directory.
     * 
     * @param f the source file.
     * @param dest the destination file or directory.
     * @return the error or <code>null</code> if there was none.
     */
    public static String mv(File f, File dest) {
        final File canonF;
        File canonDest;
        try {
            canonF = f.getCanonicalFile();
            canonDest = dest.getCanonicalFile();
        } catch (IOException e) {
            return ExceptionUtils.getStackTrace(e);
        }
        if (canonF.equals(canonDest))
            // nothing to do
            return null;
        if (canonDest.isDirectory())
            canonDest = new File(canonDest, canonF.getName());

        final File destF;
        if (canonDest.exists())
            return canonDest + " exists";
        else if (!canonDest.getParentFile().exists())
            return "parent of " + canonDest + " does not exist";
        else
            destF = canonDest;
        if (!canonF.renameTo(destF)) {
            try {
                copyDirectory(canonF, destF);
                if (destF.exists())
                    rm_R(canonF);
            } catch (IOException e) {
                return ExceptionUtils.getStackTrace(e);
            }
        }
        return null;
    }

    // transferTo() can be limited by a number of factors, like the number of bits of the system
    // if mmap is used (e.g. on Linux) or by an arbitrary magic number on Windows : 64Mb - 32Kb
    private static final int CHANNEL_MAX_COUNT = Math.min(64 * 1024 * 1024 - 32 * 1024, Integer.MAX_VALUE);

    public static void copyFile(File in, File out) throws IOException {
        copyFile(in, out, CHANNEL_MAX_COUNT);
    }

    /**
     * Copy a file. It is generally not advised to use 0 for <code>maxCount</code> since various
     * implementations have size limitations, see {@link #copyFile(File, File)}.
     * 
     * @param in the source file.
     * @param out the destination file.
     * @param maxCount the number of bytes to copy at a time, 0 meaning size of <code>in</code>.
     * @throws IOException if an error occurs.
     */
    public static void copyFile(File in, File out, long maxCount) throws IOException {
        try (final FileInputStream sourceIn = new FileInputStream(in); final FileOutputStream sourceOut = new FileOutputStream(out);) {
            final FileChannel sourceChannel = sourceIn.getChannel();
            final long size = sourceChannel.size();
            if (maxCount == 0)
                maxCount = size;
            final FileChannel destinationChannel = sourceOut.getChannel();
            long position = 0;
            while (position < size) {
                position += sourceChannel.transferTo(position, maxCount, destinationChannel);
            }
        }
    }

    public static void copyFile(File in, File out, final boolean useTime) throws IOException {
        if (!useTime || in.lastModified() != out.lastModified()) {
            copyFile(in, out);
            if (useTime)
                out.setLastModified(in.lastModified());
        }
    }

    public static void copyDirectory(File in, File out) throws IOException {
        copyDirectory(in, out, Collections.<String> emptySet());
    }

    public static final Set<String> VersionControl = CollectionUtils.createSet(".svn", "CVS");

    public static void copyDirectory(File in, File out, final Set<String> toIgnore) throws IOException {
        copyDirectory(in, out, toIgnore, false);
    }

    public static void copyDirectory(File in, File out, final Set<String> toIgnore, final boolean useTime) throws IOException {
        if (toIgnore.contains(in.getName()))
            return;

        if (in.isDirectory()) {
            if (!out.exists()) {
                out.mkdir();
            }

            String[] children = in.list();
            for (int i = 0; i < children.length; i++) {
                copyDirectory(new File(in, children[i]), new File(out, children[i]), toIgnore, useTime);
            }
        } else {
            if (!in.getName().equals("Thumbs.db")) {
                copyFile(in, out, useTime);
            }
        }
    }

    public static void copyDirectory(Path in, Path out) throws IOException {
        copyDirectory(in, out, false);
    }

    public static void copyDirectory(Path in, Path out, final boolean hardLink, final CopyOption... copyOptions) throws IOException {
        copyDirectory(in, out, Collections.<String> emptySet(), hardLink, false, Arrays.asList(copyOptions));
    }

    public static void copyDirectory(Path in, Path out, final Set<String> toIgnore, final boolean hardLink, final boolean followLinks, final List<CopyOption> copyOptions) throws IOException {
        final LinkOption[] isDirOptions = followLinks ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
        final Set<CopyOption> copyOptionsP = new HashSet<>(copyOptions);
        if (followLinks) {
            copyOptionsP.remove(LinkOption.NOFOLLOW_LINKS);
        } else {
            copyOptionsP.add(LinkOption.NOFOLLOW_LINKS);
        }
        copyDirectory(in, out, toIgnore, isDirOptions, copyOptionsP.toArray(new CopyOption[copyOptionsP.size()]), hardLink);
    }

    public static void copyDirectory(Path in, Path out, final Set<String> toIgnore, final LinkOption[] isDirOptions, final CopyOption[] copyOptions, final boolean hardLink) throws IOException {
        if (toIgnore.contains(in.getFileName().toString()))
            return;

        if (Files.isDirectory(in, isDirOptions)) {
            Files.copy(in, out, copyOptions);

            try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(in)) {
                for (final Path child : dirStream) {
                    copyDirectory(child, out.resolve(child.getFileName()), toIgnore, isDirOptions, copyOptions, hardLink);
                }
            }
            // fix up modification time of directory when done
            // (other attributes have already been copied at creation time)
            if (Arrays.asList(copyOptions).contains(StandardCopyOption.COPY_ATTRIBUTES))
                Files.setLastModifiedTime(out, Files.getLastModifiedTime(in));
        } else {
            if (!in.getFileName().toString().equals("Thumbs.db")) {
                if (hardLink)
                    Files.createLink(out, in);
                else
                    Files.copy(in, out, copyOptions);
            }
        }
    }

    /**
     * Delete recursively the passed directory. If a deletion fails, the method stops attempting to
     * delete and returns false.
     * 
     * @param dir the dir to be deleted.
     * @return <code>true</code> if all deletions were successful.
     * @deprecated callers often forget to check return value, see also {@link #rm_R(Path)}
     */
    public static boolean rmR(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < children.length; i++) {
                boolean success = rmR(children[i]);

                if (!success) {

                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    public static void rm_R(File dir) throws IOException {
        rm_R(dir.toPath());
    }

    public static void rm_R(Path dir) throws IOException {
        rm_R(dir, false);
    }

    /**
     * Delete recursively the passed file.
     * 
     * @param dir the file to delete.
     * @param mustExist what to do if <code>dir</code> is missing : <code>false</code> if it is not
     *        an error, <code>true</code> to fail.
     * @throws IOException if a deletion fails.
     */
    public static void rm_R(final Path dir, final boolean mustExist) throws IOException {
        if (!mustExist && !Files.exists(dir))
            return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // The directory is now empty so delete it
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void rm(File f) throws IOException {
        if (f.exists() && !f.delete())
            throw new IOException("cannot delete " + f);
    }

    public static final File mkdir_p(File dir) throws IOException {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("cannot create directory " + dir);
            }
        }
        return dir;
    }

    /**
     * Create all ancestors of <code>f</code>.
     * 
     * @param f any file whose ancestors should be created.
     * @return <code>f</code>.
     * @throws IOException if ancestors cannot be created.
     */
    public static final File mkParentDirs(File f) throws IOException {
        final File parentFile = f.getParentFile();
        if (parentFile != null)
            mkdir_p(parentFile);
        return f;
    }

    // **io

    /**
     * Read a file line by line with the default encoding and returns the concatenation of these.
     * 
     * @param f the file to read.
     * @return the content of f.
     * @throws IOException if a pb occur while reading.
     */
    public static final String read(File f) throws IOException {
        return read(new InputStreamReader(new FileInputStream(f)));
    }

    /**
     * Read a file line by line and returns the concatenation of these.
     * 
     * @param f the file to read.
     * @param charset the encoding of <code>f</code>.
     * @return the content of f.
     * @throws IOException if a pb occur while reading.
     */
    public static final String read(File f, String charset) throws IOException {
        return read(new InputStreamReader(new FileInputStream(f), charset));
    }

    public static final String readUTF8(File f) throws IOException {
        return readUTF8(new FileInputStream(f));
    }

    public static final String readUTF8(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public static final String readUTF8(InputStream ins) throws IOException {
        return read(ins, StringUtils.UTF8);
    }

    public static final String read(File f, Charset charset) throws IOException {
        return read(new FileInputStream(f), charset);
    }

    public static final String read(InputStream ins, Charset charset) throws IOException {
        final Reader reader;
        if (charset == null)
            reader = new InputStreamReader(ins);
        else
            reader = new InputStreamReader(ins, charset);
        return read(reader);
    }

    public static final String read(final Reader reader) throws IOException {
        return read(reader, 8192);
    }

    public static final String read(final Reader reader, final int bufferSize) throws IOException {
        final StringBuilder sb = new StringBuilder();
        final char[] buffer = new char[bufferSize];
        final BufferedReader in = new BufferedReader(reader);
        try {
            while (true) {
                final int count = in.read(buffer);
                if (count == -1)
                    break;
                sb.append(buffer, 0, count);
            }
        } finally {
            in.close();
        }
        return sb.toString();
    }

    /**
     * Read the whole content of a file.
     * 
     * @param f the file to read.
     * @return its content.
     * @throws IOException if a pb occur while reading.
     * @see Files#readAllBytes(java.nio.file.Path)
     */
    public static final byte[] readBytes(File f) throws IOException {
        // works for /proc files which report 0 size
        return Files.readAllBytes(f.toPath());
    }

    /**
     * Write the passed string with default charset to the passed file, truncating it.
     * 
     * @param s the string.
     * @param f the file.
     * @throws IOException if an error occurs.
     * @deprecated use {@link #writeUTF8(Path, String, OpenOption...)} or
     *             {@link #write2Step(String, Path)}
     */
    public static void write(String s, File f) throws IOException {
        write2Step(s, f, Charset.defaultCharset(), false);
    }

    public static void writeUTF8(String s, File f) throws IOException {
        writeUTF8(f.toPath(), s);
    }

    public static void writeUTF8(Path path, String s, OpenOption... options) throws IOException {
        Files.write(path, s.getBytes(StandardCharsets.UTF_8), options);
    }

    public static void write2Step(String s, File f, Charset charset, boolean append) throws IOException {
        write2Step(s, f.toPath(), charset, append);
    }

    public static void write2Step(final String s, final Path f) throws IOException {
        // UTF_8 default like Files.newBufferedReader()
        write2Step(s, f, StandardCharsets.UTF_8);
    }

    public static void write2Step(final String s, final Path f, final Charset charset) throws IOException {
        write2Step(s, f, charset, false);
    }

    public static void write2Step(final String s, final Path f, final Charset charset, final boolean append) throws IOException {
        // create temporary file in the same directory so we can move it
        final Path tmpFile = Files.createTempFile(f.toAbsolutePath().getParent(), null, null);
        try {
            // 1. write elsewhere
            final byte[] bs = charset == null ? s.getBytes() : s.getBytes(charset);
            if (append) {
                // REPLACE_EXISTING since tmpFile exists
                Files.copy(f, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                Files.write(tmpFile, bs, StandardOpenOption.APPEND);
            } else {
                Files.write(tmpFile, bs);
            }
            // 2. move into place (cannot use ATOMIC_MOVE because f might exists ; and we would need
            // to handle AtomicMoveNotSupportedException)
            Files.move(tmpFile, f, StandardCopyOption.REPLACE_EXISTING);
            // don't try to delete if move() successful
        } catch (RuntimeException | Error | IOException e) {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (Exception e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    /**
     * Create a writer for the passed file, and write the XML declaration.
     * 
     * @param f a file
     * @return a writer with the same encoding as the XML.
     * @throws IOException if an error occurs.
     * @see StreamUtils#createXMLWriter(java.io.OutputStream)
     */
    public static BufferedWriter createXMLWriter(final File f) throws IOException {
        FileUtils.mkParentDirs(f);
        final FileOutputStream outs = new FileOutputStream(f);
        try {
            return StreamUtils.createXMLWriter(outs);
        } catch (RuntimeException e) {
            outs.close();
            throw e;
        } catch (IOException e) {
            outs.close();
            throw e;
        }
    }

    /**
     * Create an UTF-8 buffered writer.
     * 
     * @param f the file to write to.
     * @return a buffered writer.
     * @throws FileNotFoundException if the file cannot be opened.
     */
    public static BufferedWriter createWriter(final File f) throws FileNotFoundException {
        return createWriter(f, StringUtils.UTF8);
    }

    public static BufferedWriter createWriter(final File f, final Charset cs) throws FileNotFoundException {
        final FileOutputStream outs = new FileOutputStream(f);
        try {
            return new BufferedWriter(new OutputStreamWriter(outs, cs));
        } catch (RuntimeException e) {
            try {
                outs.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            throw e;
        }
    }

    /**
     * Execute the passed transformer with the lock on the passed file.
     * 
     * @param <T> return type.
     * @param f the file to lock.
     * @param transf what to do on the file.
     * @return what <code>transf</code> returns.
     * @throws IOException if an error occurs while locking the file.
     * @throws X if an error occurs while using the file.
     */
    public static final <T, X extends Exception> T doWithLock(final File f, ExnTransformer<RandomAccessFile, T, X> transf) throws IOException, X {
        mkParentDirs(f);
        // don't use FileOutputStream : it truncates the file on creation
        // we need write to obtain lock
        try (final RandomAccessFile out = new RandomAccessFile(f, "rw")) {
            out.getChannel().lock();
            return transf.transformChecked(out);
        }
        // the lock is released on close()
    }

    private static final Map<URL, File> files = new HashMap<URL, File>();

    private static final File getShortCutFile() throws IOException {
        return getFile(FileUtils.class.getResource("shortcut.vbs"));
    }

    // windows cannot execute a string, it demands a file
    public static final File getFile(final URL url) throws IOException {
        // avoid unnecessary IO if already a file
        File urlFile = null;
        // inexpensive comparison before trying to convert to URI and call the File constructor
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            try {
                urlFile = new File(url.toURI());
            } catch (Exception e) {
                Log.get().log(Level.FINER, "couldn't convert to file " + url, e);
            }
        }
        if (urlFile != null)
            return urlFile;

        final File shortcutFile;
        final File currentFile = files.get(url);
        if (currentFile == null || !currentFile.exists()) {
            shortcutFile = File.createTempFile("windowsIsLame", ".vbs");
            // ATTN if the VM is not terminated normally, the file won't be deleted
            // perhaps a thread to delete the file after a certain amount of time
            shortcutFile.deleteOnExit();
            files.put(url, shortcutFile);
            try (final InputStream stream = url.openStream(); final FileOutputStream out = new FileOutputStream(shortcutFile);) {
                StreamUtils.copy(stream, out);
            }
        } else
            shortcutFile = currentFile;
        return shortcutFile;
    }

    /**
     * Create a symbolic link from <code>link</code> to <code>target</code>.
     * 
     * @param target the target of the link, eg ".".
     * @param link the file to create or replace, eg "l".
     * @return the link if the creation was successfull, <code>null</code> otherwise, eg "l.LNK".
     * @throws IOException if an error occurs.
     */
    public static final File ln(final File target, final File link) throws IOException {
        final Process ps;
        final File res;
        if (OSFamily.getInstance() == OSFamily.Windows) {
            // using the .vbs since it doesn't depends on cygwin
            // and cygwin's ln is weird :
            // 1. needs CYGWIN=winsymlinks to create a shortcut, but even then "ln -f" doesn't work
            // since it tries to delete l instead of l.LNK
            // 2. it sets the system flag so "dir" doesn't show the shortcut (unless you add /AS)
            // 3. the shortcut is recognized as a symlink thanks to a special attribute that can get
            // lost (e.g. copying in eclipse)
            ps = startDiscardingOutput("cscript", getShortCutFile().getAbsolutePath(), link.getAbsolutePath(), target.getCanonicalPath());
            res = new File(link.getParentFile(), link.getName() + ".LNK");
        } else {
            final String rel = FileUtils.relative(link.getAbsoluteFile().getParentFile(), target);
            // add -f to replace existing links
            // add -n so that ln -sf aDir anExistantLinkToIt succeed
            final String[] cmdarray = { "ln", "-sfn", rel, link.getAbsolutePath() };
            ps = startDiscardingOutput(cmdarray);
            res = link;
        }
        try {
            final int exitValue = ps.waitFor();
            if (exitValue == 0)
                return res;
            else
                throw new IOException("Abnormal exit value: " + exitValue);
        } catch (InterruptedException e) {
            throw ExceptionUtils.createExn(IOException.class, "interrupted", e);
        }
    }

    /**
     * Resolve a symbolic link or a windows shortcut.
     * 
     * @param link the shortcut, e.g. shortcut.lnk.
     * @return the target of <code>link</code>, <code>null</code> if not found, e.g. target.txt.
     * @throws IOException if an error occurs.
     */
    public static final File readlink(final File link) throws IOException {
        final Process ps;
        if (OSFamily.getInstance() == OSFamily.Windows) {
            ps = Runtime.getRuntime().exec(new String[] { "cscript", "//NoLogo", getShortCutFile().getAbsolutePath(), link.getAbsolutePath() });
        } else {
            // add -f to canonicalize
            ps = Runtime.getRuntime().exec(new String[] { "readlink", "-f", link.getAbsolutePath() });
        }
        try {
            ps.getErrorStream().close();
            final String res;
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(ps.getInputStream()));) {
                res = reader.readLine();
            }
            if (ps.waitFor() != 0 || res == null || res.length() == 0)
                return null;
            else
                return new File(res);
        } catch (InterruptedException e) {
            throw ExceptionUtils.createExn(IOException.class, "interrupted", e);
        }
    }

    // from guava/src/com/google/common/io/Files.java
    /** Maximum loop count when creating temp directories. */
    private static final int TEMP_DIR_ATTEMPTS = 10000;

    /**
     * Atomically creates a new directory somewhere beneath the system's temporary directory (as
     * defined by the {@code java.io.tmpdir} system property), and returns its name.
     * 
     * <p>
     * Use this method instead of {@link File#createTempFile(String, String)} when you wish to
     * create a directory, not a regular file. A common pitfall is to call {@code createTempFile},
     * delete the file and create a directory in its place, but this leads a race condition which
     * can be exploited to create security vulnerabilities, especially when executable files are to
     * be written into the directory.
     * 
     * <p>
     * This method assumes that the temporary volume is writable, has free inodes and free blocks,
     * and that it will not be called thousands of times per second.
     * 
     * @param prefix the prefix string to be used in generating the directory's name.
     * @return the newly-created directory.
     * @throws IllegalStateException if the directory could not be created.
     * @deprecated use
     *             {@link Files#createTempDirectory(String, java.nio.file.attribute.FileAttribute...)}
     */
    public static File createTempDir(final String prefix) {
        final File baseDir = new File(System.getProperty("java.io.tmpdir"));
        final String baseName = prefix + System.currentTimeMillis() + "-";

        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
            final File tempDir = new File(baseDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory within " + TEMP_DIR_ATTEMPTS + " attempts (tried " + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }

    /**
     * Tries to open the passed file as if it were graphically opened by the current user (respect
     * user's "open with"). If a native way to open the file can't be found, tries the passed list
     * of executables.
     * 
     * @param f the file to open.
     * @param executables a list of executables to try, e.g. ["ooffice", "soffice"].
     * @throws IOException if the file can't be opened.
     */
    public static final void open(File f, String[] executables) throws IOException {
        if (!f.exists()) {
            throw new FileNotFoundException(f.getAbsolutePath() + " not found");
        }
        try {
            openNative(f);
        } catch (IOException exn) {
            for (int i = 0; i < executables.length; i++) {
                final String executable = executables[i];
                try {
                    startDiscardingOutput(executable, f.getCanonicalPath());
                    return;
                } catch (IOException e) {
                    exn.addSuppressed(new IOException("unable to open with " + executable, e));
                    // try the next one
                }
            }
            throw new IOException("unable to open " + f, exn);
        }
    }

    /**
     * Open the passed file as if it were graphically opened by the current user (user's "open
     * with").
     * 
     * @param f the file to open.
     * @throws IOException if f couldn't be opened.
     */
    private static final void openNative(File f) throws IOException {
        openNative(f.getCanonicalPath());
    }

    private static final void openNative(URI uri) throws IOException {
        openNative(uri.toASCIIString());
    }

    private static final void openNative(String param) throws IOException {
        final OSFamily os = OSFamily.getInstance();
        final String[] cmdarray;
        if (os == OSFamily.Windows) {
            cmdarray = new String[] { "cmd", "/c", "start", "\"\"", param };
        } else if (os == OSFamily.Mac) {
            cmdarray = new String[] { "open", param };
        } else if (os instanceof Unix) {
            cmdarray = new String[] { "xdg-open", param };
        } else {
            throw new IOException("unknown way to open " + param);
        }
        try {
            final Process ps = startDiscardingOutput(cmdarray);
            // can wait since the command return as soon as the native application is launched
            // (i.e. this won't wait 30s for OpenOffice)
            final int res = ps.waitFor();
            if (res != 0)
                throw new IOException("error (" + res + ") executing " + Arrays.asList(cmdarray));
        } catch (InterruptedException e) {
            throw ExceptionUtils.createExn(IOException.class, "interrupted waiting for " + Arrays.asList(cmdarray), e);
        }
    }

    private static final Process startDiscardingOutput(final String... command) throws IOException {
        return startDiscardingOutput(new ProcessBuilder(command));
    }

    private static final Process startDiscardingOutput(final ProcessBuilder pb) throws IOException {
        final Process res = pb.redirectOutput(ProcessStreams.DISCARD).redirectError(ProcessStreams.DISCARD).start();
        res.getOutputStream().close();
        return res;
    }

    static final boolean gnomeRunning() {
        try {
            final Process ps = startDiscardingOutput("pgrep", "-u", System.getProperty("user.name"), "nautilus");
            // no need for output, use exit status
            return ps.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static final String XML_TYPE = "text/xml";
    private static final Map<String, String> ext2mime;
    private static final SetMap<String, String> mime2ext;

    static {
        mime2ext = new SetMap<String, String>(Mode.NULL_FORBIDDEN);
        mime2ext.putCollection(XML_TYPE, ".xml");
        mime2ext.putCollection("image/jpeg", ".jpg", ".jpeg");
        mime2ext.putCollection("image/png", ".png");
        mime2ext.putCollection("image/tiff", ".tiff", ".tif");
        mime2ext.putCollection("application/pdf", ".pdf");

        mime2ext.putCollection("application/vnd.oasis.opendocument.spreadsheet", ".ods");
        mime2ext.putCollection("application/vnd.oasis.opendocument.text", ".odt");
        mime2ext.putCollection("application/vnd.oasis.opendocument.presentation", ".odp");
        mime2ext.putCollection("application/vnd.oasis.opendocument.graphics", ".odg");

        ext2mime = new HashMap<String, String>();
        for (final Entry<String, Set<String>> e : mime2ext.entrySet()) {
            final String m = e.getKey();
            for (final String ext : e.getValue()) {
                if (ext2mime.put(ext, m) != null)
                    Log.get().info("Duplicate extension : " + ext);
            }
        }
    }

    /**
     * Try to guess the media type of the passed file name (see
     * <a href="http://www.iana.org/assignments/media-types">iana</a>).
     * 
     * @param fname a file name.
     * @return its mime type.
     */
    public static final String findMimeType(String fname) {
        for (final Map.Entry<String, String> e : ext2mime.entrySet()) {
            if (fname.toLowerCase().endsWith(e.getKey()))
                return e.getValue();
        }
        return null;
    }

    public static final Set<String> getExtensionsFromMimeType(final String mimetype) {
        return mime2ext.get(mimetype);
    }

    /**
     * Return the string after the last dot.
     * 
     * @param fname a name, e.g. "test.odt" or "sans".
     * @return the extension, e.g. "odt" or <code>null</code>.
     */
    public static final String getExtension(String fname) {
        return getExtension(fname, false);
    }

    public static final String getExtension(final String fname, final boolean withDot) {
        final int lastIndex = fname.lastIndexOf('.');
        return lastIndex < 0 ? null : fname.substring(lastIndex + (withDot ? 0 : 1));
    }

    /**
     * Chars not valid in filenames.
     */
    public static final Collection<Character> INVALID_CHARS;

    /**
     * An escaper suitable for producing valid filenames.
     */
    public static final Escaper FILENAME_ESCAPER = new StringUtils.Escaper('\'', 'Q');

    static private final String WS = "\\p{javaWhitespace}";
    static private final Pattern WS_PATTERN = Pattern.compile(WS + "+");
    static private final Pattern CONTROL_PATTERN = Pattern.compile("[\\p{IsCc}\\p{IsCf}&&[^" + WS + "]]+");
    static private final Pattern INVALID_CHARS_PATTERN;

    static {
        // from windows explorer
        // http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx
        // Naming Files, Paths, and Namespaces
        // on Mac only '/' and ':', on Linux only '/'
        FILENAME_ESCAPER.add('"', 'D').add(':', 'C').add('/', 'S').add('\\', 'A');
        FILENAME_ESCAPER.add('<', 'L').add('>', 'G').add('*', 'R').add('|', 'P').add('?', 'M');
        INVALID_CHARS = FILENAME_ESCAPER.getEscapedChars();
        INVALID_CHARS_PATTERN = Pattern.compile("[" + CollectionUtils.join(INVALID_CHARS, "") + "]");
    }

    /**
     * Sanitize a name. Remove control characters, trim, and replace {@link #INVALID_CHARS} by '_'.
     * 
     * @param name an arbitrary name.
     * @return a name suitable for any file system.
     */
    static public final String sanitize(String name) {
        // remove control and format characters (except white spaces)
        name = CONTROL_PATTERN.matcher(name).replaceAll("");
        // only use one regular space (must be done after removing control characters as if they are
        // between spaces we want only one space to remain)
        name = WS_PATTERN.matcher(name).replaceAll(" ");
        // leading and trailing spaces are hard to see (and illegal in Explorer)
        name = name.trim();

        // replace all invalid characters with _
        name = INVALID_CHARS_PATTERN.matcher(name).replaceAll("_");

        return name;
    }

    public static final FileFilter DIR_FILTER = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.isDirectory();
        }
    };
    public static final FileFilter REGULAR_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.isFile();
        }
    };
    public static final Filter<Path> DIR_PATH_FILTER = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
        }
    };

    /**
     * Return a filter that select regular files ending in <code>ext</code>.
     * 
     * @param ext the end of the name, eg ".xml".
     * @return the corresponding filter.
     */
    public static final FileFilter createEndFileFilter(final String ext) {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isFile() && f.getName().endsWith(ext);
            }
        };
    }

    /**
     * How to merge the group and others portion of permissions for non-POSIX FS.
     * 
     * @author sylvain
     * @see FileUtils#setFilePermissionsFromPOSIX(File, String, GroupAndOthers)
     */
    public static enum GroupAndOthers {
        REQUIRE_SAME {
            @Override
            protected Set<Permission> getNonEqual(Set<Permission> groupPerms, Set<Permission> otherPerms) {
                throw new IllegalArgumentException("Different permissions : " + groupPerms + " != " + otherPerms);
            }
        },
        PERMISSIVE {
            @Override
            protected Set<Permission> getNonEqual(Set<Permission> groupPerms, Set<Permission> otherPerms) {
                final EnumSet<Permission> res = EnumSet.noneOf(Permission.class);
                res.addAll(groupPerms);
                res.addAll(otherPerms);
                return res;
            }
        },
        OTHERS {
            @Override
            protected Set<Permission> getNonEqual(Set<Permission> groupPerms, Set<Permission> otherPerms) {
                return otherPerms;
            }
        },
        RESTRICTIVE {
            @Override
            protected Set<Permission> getNonEqual(Set<Permission> groupPerms, Set<Permission> otherPerms) {
                final EnumSet<Permission> res = EnumSet.allOf(Permission.class);
                res.retainAll(groupPerms);
                res.retainAll(otherPerms);
                return res;
            }
        };

        public final Set<Permission> getPermissions(final Set<Permission> groupPerms, final Set<Permission> otherPerms) {
            if (groupPerms.equals(otherPerms)) {
                return groupPerms;
            } else {
                return getNonEqual(groupPerms, otherPerms);
            }
        }

        public final Set<Permission> getPermissions(final String posixPerms) {
            final Set<Permission> groupPerms = Permission.fromString(posixPerms.substring(3, 6));
            final Set<Permission> otherPerms = Permission.fromString(posixPerms.substring(6, 9));
            return this.getPermissions(groupPerms, otherPerms);
        }

        protected abstract Set<Permission> getNonEqual(final Set<Permission> groupPerms, final Set<Permission> otherPerms);

    }

    public static final String setPermissions(final Path p, final String posixPerms) throws IOException {
        return setPermissions(p, posixPerms, GroupAndOthers.RESTRICTIVE);
    }

    /**
     * Use {@link PosixFileAttributeView#setPermissions(Set)} if possible, otherwise use
     * {@link #setFilePermissionsFromPOSIX(File, String, GroupAndOthers)}.
     * 
     * @param p the path to change.
     * @param posixPerms the new permissions to apply.
     * @param groupAndOthers only for non-POSIX FS, how to merge group and others portion.
     * @return the permission applied, 9 characters for POSIX, 6 for non-POSIX (i.e. 3 for owner, 3
     *         for the rest), <code>null</code> if some permissions couldn't be applied (only on
     *         non-POSIX).
     * @throws IOException if permissions couldn't be applied.
     */
    public static final String setPermissions(final Path p, final String posixPerms, final GroupAndOthers groupAndOthers) throws IOException {
        final String res;
        final PosixFileAttributeView view = Files.getFileAttributeView(p, PosixFileAttributeView.class);
        if (view != null) {
            view.setPermissions(PosixFilePermissions.fromString(posixPerms));
            res = posixPerms;
        } else {
            // final Set<Permission> notOwnerPerms = setFilePermissions(p.toFile(), pfp,
            // groupAndOthers);
            final Set<Permission> notOwnerPerms = setFilePermissionsFromPOSIX(p.toFile(), posixPerms, groupAndOthers);
            res = notOwnerPerms == null ? null : posixPerms.substring(0, 3) + Permission.get3chars(notOwnerPerms);
        }
        return res;
    }

    public static final Set<Permission> setFilePermissionsFromPOSIX(final File f, final String posixPerms) {
        return setFilePermissionsFromPOSIX(f, posixPerms, GroupAndOthers.RESTRICTIVE);
    }

    /**
     * This method doesn't need POSIX but must merge permissions before applying them.
     * 
     * @param f the file to change.
     * @param posixPerms the POSIX permissions to merge.
     * @param groupAndOthers how to merge.
     * @return the merged permissions for the "not owner" portion, or <code>null</code> if some
     *         permissions couldn't be set.
     * @see #setFilePermissions(File, Set, Set)
     */
    public static final Set<Permission> setFilePermissionsFromPOSIX(final File f, final String posixPerms, final GroupAndOthers groupAndOthers) {
        if (posixPerms.length() != 9)
            throw new IllegalArgumentException("Invalid mode : " + posixPerms);
        final Set<Permission> ownerPerms = Permission.fromString(posixPerms.substring(0, 3));
        final Set<Permission> notOwnerPerms = groupAndOthers.getPermissions(posixPerms);
        assert notOwnerPerms != null;
        final boolean success = setFilePermissions(f, ownerPerms, notOwnerPerms);
        return success ? notOwnerPerms : null;
    }

    /**
     * Use {@link File} methods to set permissions. This works everywhere but group and others are
     * treated as the same.
     * 
     * @param f the file to change.
     * @param owner the permissions for the owner.
     * @param notOwner the permissions for not the owner.
     * @return <code>true</code> if all asked permissions were set.
     * @see File#setReadable(boolean, boolean)
     * @see File#setWritable(boolean, boolean)
     * @see File#setExecutable(boolean, boolean)
     */
    public static final boolean setFilePermissions(final File f, final Set<Permission> owner, final Set<Permission> notOwner) {
        boolean res = setFilePermissions(f, notOwner, false);
        if (!owner.equals(notOwner)) {
            res &= setFilePermissions(f, owner, true);
        }
        return res;
    }

    public static final boolean setFilePermissions(final File f, final Set<Permission> perms, final boolean ownerOnly) {
        boolean res = f.setReadable(perms.contains(Permission.READ), ownerOnly);
        res &= f.setWritable(perms.contains(Permission.WRITE), ownerOnly);
        res &= f.setExecutable(perms.contains(Permission.EXECUTE), ownerOnly);
        return res;
    }

    public static enum Permission {
        READ, WRITE, EXECUTE;

        public static final Permission R = READ;
        public static final Permission W = WRITE;
        public static final Permission X = EXECUTE;
        public static final Map<String, Set<Permission>> FROM_STRING = new HashMap<>();
        public static final Pattern MINUS_PATTERN = Pattern.compile("-+");
        static {
            putString("---", Collections.<Permission> emptySet());
            putString("--x", Collections.singleton(EXECUTE));
            putString("-w-", Collections.singleton(WRITE));
            putString("-wx", EnumSet.of(WRITE, EXECUTE));
            putString("r--", Collections.singleton(READ));
            putString("r-x", EnumSet.of(READ, EXECUTE));
            putString("rw-", EnumSet.of(READ, WRITE));
            putString("rwx", EnumSet.allOf(Permission.class));
        }

        static private final void putString(final String str, final EnumSet<Permission> set) {
            putString(str, Collections.unmodifiableSet(set));
        }

        static private final void putString(final String str, final Set<Permission> unmodifiableSet) {
            FROM_STRING.put(str, unmodifiableSet);
            FROM_STRING.put(MINUS_PATTERN.matcher(str).replaceAll(""), unmodifiableSet);
        }

        static public final Set<Permission> fromString(final String str) {
            final Set<Permission> res = FROM_STRING.get(str);
            if (res == null)
                throw new IllegalArgumentException("Invalid string : " + str);
            return res;
        }

        public static final String get3chars(final Set<Permission> perms) {
            return get3chars(perms.contains(READ), perms.contains(WRITE), perms.contains(EXECUTE));
        }

        private static final String get3chars(final boolean read, final boolean write, final boolean exec) {
            final StringBuilder sb = new StringBuilder(3);
            sb.append(read ? 'r' : '-');
            sb.append(write ? 'w' : '-');
            sb.append(exec ? 'x' : '-');
            return sb.toString();
        }
    }
}
