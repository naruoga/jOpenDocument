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

import org.jopendocument.util.OSFamily.Unix;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * A desktop environment like Gnome or MacOS.
 * 
 * @author Sylvain CUAZ
 * @see #getDE()
 */
@ThreadSafe
public abstract class DesktopEnvironment {

    static public final class Gnome extends DesktopEnvironment {

        static private final String getTextContent(final Element parentElem, final String childName) {
            final NodeList children = parentElem.getElementsByTagName(childName);
            if (children.getLength() != 1)
                throw new IllegalStateException("Not one child " + childName + " in " + parentElem);
            return children.item(0).getTextContent();
        }

        private final String name;

        public Gnome(final String name) {
            this.name = name;
        }

        @Override
        protected String findVersion() {
            try {
                if (this.name.equals("gnome")) {
                    // e.g. GNOME gnome-about 2.24.1
                    final String line = cmdSubstitution(Runtime.getRuntime().exec(new String[] { "gnome-about", "--version" }));
                    final String[] words = line.split(" ");
                    return words[words.length - 1];
                } else if (this.name.equals("gnome3")) {
                    final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    // <gnome-version>
                    // <platform>3</platform>
                    // <minor>18</minor>
                    // <micro>2</micro>
                    // </gnome-version>
                    final Element root = builder.parse(new File("/usr/share/gnome/gnome-version.xml")).getDocumentElement();
                    return getTextContent(root, "platform") + '.' + getTextContent(root, "minor") + '.' + getTextContent(root, "micro");
                } else {
                    throw new IllegalStateException("unknown name : " + this.name);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    static public final class MATE extends DesktopEnvironment {

        @Override
        protected String findVersion() {
            try {
                // e.g. GNOME gnome-about 2.24.1
                final String line = cmdSubstitution(Runtime.getRuntime().exec(new String[] { "mate-about", "--version" }));
                final String[] words = line.split(" ");
                return words[words.length - 1];
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    static public final class KDE extends DesktopEnvironment {

        private static final Pattern versionPattern = Pattern.compile("^KDE: (.*)$", Pattern.MULTILINE);

        @Override
        protected String findVersion() {
            try {
                // Qt: 3.3.8b
                // KDE: 3.5.10
                // kde-config: 1.0
                final String line = cmdSubstitution(Runtime.getRuntime().exec(new String[] { "kde-config", "--version" }));
                final Matcher matcher = versionPattern.matcher(line);
                matcher.find();
                return matcher.group(1);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    static public final class XFCE extends DesktopEnvironment {
        // xfce4-about 4.11.1 (Xfce 4.10)
        // Copyright (c) 2008-2011
        private static final Pattern versionPattern = Pattern.compile("^xfce4-about.+\\(\\p{Alnum}+\\p{Blank}+(.+)\\)$", Pattern.MULTILINE);

        @Override
        protected String findVersion() {
            try {
                final String line = cmdSubstitution(Runtime.getRuntime().exec(new String[] { "xfce4-about", "--version" }));
                final Matcher matcher = versionPattern.matcher(line);
                matcher.find();
                return matcher.group(1);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    static public final class Unknown extends DesktopEnvironment {
        @Override
        protected String findVersion() {
            return "";
        }
    }

    static private class DEisOS extends DesktopEnvironment {
        @Override
        protected String findVersion() {
            return System.getProperty("os.version");
        }
    }

    static public final class Windows extends DEisOS {
        // e.g. bash.exe
        public String quoteParamForGCC(String s) {
            return StringUtils.doubleQuote(s);
        }
    }

    static public final class Mac extends DEisOS {

        // From CarbonCore/Folders.h
        public static final String kDocumentsDirectory = "docs";
        public static final String kPreferencesDirectory = "pref";
        private static Class<?> FileManagerClass;
        private static Short kUserDomain;
        private static Method OSTypeToInt;

        private static Class<?> getFileManagerClass() {
            if (FileManagerClass == null) {
                try {
                    FileManagerClass = Class.forName("com.apple.eio.FileManager");
                    OSTypeToInt = FileManagerClass.getMethod("OSTypeToInt", String.class);
                    kUserDomain = (Short) FileManagerClass.getField("kUserDomain").get(null);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return FileManagerClass;
        }

        @Override
        public File getDocumentsFolder() {
            return new File(System.getProperty("user.home"), "Documents/");
        }

        // There was a warning until JRE v16, now "--add-exports
        // java.desktop/com.apple.eio=ALL-UNNAMED" is needed. Further this needs the EDT which is
        // undesirable just to get some paths.
        // https://bugs.openjdk.java.net/browse/JDK-8187981
        @Deprecated
        public File getFolder(String type) {
            try {
                final Method findFolder = getFileManagerClass().getMethod("findFolder", Short.TYPE, Integer.TYPE);
                final RunnableFuture<String> f = new FutureTask<>(() -> (String) findFolder.invoke(null, kUserDomain, OSTypeToInt.invoke(null, type)));
                // EDT needed at least for JRE v14-16 on macOS 10.15, otherwise the VM crashes :
                // Problematic frame: __NSAssertMainEventQueueIsCurrentEventQueue_block_invoke or
                // "The current event queue and the main event queue are not the same. This is
                // probably because _TSGetMainThread was called for the first time off the main
                // thread."
                if (SwingUtilities.isEventDispatchThread())
                    f.run();
                else
                    SwingUtilities.invokeLater(f);
                final String path = f.get();
                return new File(path);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public File getAppDir(final String bundleID) throws IOException {
            // we used to ask for the URL of the application file but since 10.7 it returns a
            // file reference URL like "file:///.file/id=6723689.35865"
            final ProcessBuilder processBuilder = new ProcessBuilder("osascript", "-e",
                    "tell application id \"com.apple.Finder\" to POSIX path of (application file id \"" + bundleID + "\" as string)");
            // if not found prints nothing to out and a cryptic error to the standard error stream
            final String dir = cmdSubstitution(processBuilder.start()).trim();
            return dir.length() == 0 ? null : new File(dir);
        }
    }

    /**
     * Execute the passed command and test its return code.
     * 
     * @param command the command to {@link Runtime#exec(String[]) execute}.
     * @return <code>false</code> if the {@link Process#waitFor() return code} is not 0 or an
     *         exception is thrown.
     * @throws RTInterruptedException if this is interrupted while waiting.
     */
    public static final boolean test(final String... command) throws RTInterruptedException {
        try {
            return Runtime.getRuntime().exec(command).waitFor() == 0;
        } catch (InterruptedException e) {
            throw new RTInterruptedException(e);
        } catch (IOException e) {
            Log.get().finer(e.getLocalizedMessage());
            return false;
        }
    }

    public final static String cmdSubstitution(Process p) throws IOException {
        return cmdSubstitution(p, null);
    }

    public final static String cmdSubstitution(final Process p, final Charset encoding) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(100 * 1024);
        // some programs won't write anything until they read everything (e.g. powershell.exe)
        p.getOutputStream().close();
        p.getErrorStream().close();
        StreamUtils.copy(p.getInputStream(), out);
        p.getInputStream().close();
        return encoding == null ? out.toString() : out.toString(encoding.name());
    }

    private static final String detectXDG() {
        String res = null;
        InputStream scriptIns = null;
        try {
            scriptIns = DesktopEnvironment.class.getResourceAsStream("DesktopEnvironmentXDG.sh");
            final Process ps = new ProcessBuilder("sh").start();
            ps.getErrorStream().close();
            StreamUtils.copy(scriptIns, ps.getOutputStream());
            ps.getOutputStream().close();
            res = FileUtils.readUTF8(ps.getInputStream()).trim();
            ps.getInputStream().close();
            if (ps.waitFor() != 0)
                throw new IllegalStateException("Not OK : " + ps.exitValue());
        } catch (Exception e) {
            Log.get().fine(e.getLocalizedMessage());
        } finally {
            if (scriptIns != null) {
                try {
                    scriptIns.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return res;
    }

    private static final DesktopEnvironment detectDE() {
        final OSFamily os = OSFamily.getInstance();
        if (os == OSFamily.Windows) {
            return new Windows();
        } else if (os == OSFamily.Mac) {
            return new Mac();
        } else if (os instanceof Unix) {
            final String de = detectXDG();
            if (de.equals("xfce"))
                return new XFCE();
            else if (de.equals("mate"))
                return new MATE();
            else if (de.equals("kde"))
                return new KDE();
            else if (de.startsWith("gnome"))
                return new Gnome(de);
        }
        return new Unknown();
    }

    @GuardedBy("DesktopEnvironment.class")
    private static DesktopEnvironment DE = null;

    public synchronized static final DesktopEnvironment getDE() {
        if (DE == null) {
            DE = detectDE();
        }
        return DE;
    }

    public synchronized static final void resetDE() {
        DE = null;
    }

    @GuardedBy("this")
    private String version;

    private DesktopEnvironment() {
        this.version = null;
    }

    protected abstract String findVersion();

    public synchronized final String getVersion() {
        if (this.version == null)
            this.version = this.findVersion();
        return this.version;
    }

    // where to write user-visible files (e.g. spreadsheets, exports)
    public File getDocumentsFolder() {
        return FileSystemView.getFileSystemView().getDefaultDirectory();
    }

    // on some systems arguments are not passed correctly by ProcessBuilder
    public final String quoteParamForExec(String s) {
        return Platform.getInstance().getProcessArg(s);
    }

    @Override
    public String toString() {
        return "DesktopEnvironment " + this.getClass().getSimpleName();
    }

    public static void main(String[] args) {
        // removes default
        LogUtils.rmRootHandlers();
        // add console handler
        LogUtils.setUpConsoleHandler();
        Log.get().setLevel(Level.FINE);
        final DesktopEnvironment de = getDE();
        System.out.println(de + " version " + de.getVersion());
    }
}
