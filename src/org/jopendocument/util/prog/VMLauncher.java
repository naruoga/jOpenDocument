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

package org.jopendocument.util.prog;

import org.jopendocument.util.FileUtils;
import org.jopendocument.util.OSFamily;
import org.jopendocument.util.OSFamily.Unix;
import org.jopendocument.util.ProcessStreams;
import org.jopendocument.util.PropertiesUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class meant to be used as the main class of a jar and which launch another instance of the Java
 * VM.
 * 
 * @author Sylvain
 * @see #launch(String, List)
 */
public abstract class VMLauncher {

    private static final String PROPERTIES_EXT = ".properties";
    /**
     * Boolean system property, if set to <code>true</code> then {@link #restart(Class, List)} will
     * simply return <code>null</code>. Useful e.g. when using IDE launch configuration (to debug).
     */
    static public final String NO_RESTART = "vm.noRestart";

    // Explicitly passed to jpackage
    static public final String APPDIR_SYSPROP = "jpackage.app.dir";

    // Automatically set by the jpackage launcher (could be set explicitly using --java-options
    // '-Djpackage.app-path=$APPDIR/../../bin/launcher' if jpackage ever changes that)
    static public final String APP_EXE_SYSPROP = "jpackage.app-path";

    // The path to the app directory with the jar
    public static final File getJPackageAppDir() {
        final String appPath = System.getProperty(APPDIR_SYSPROP, "");
        return appPath.isEmpty() ? null : new File(appPath);
    }

    // The path to the executable
    private static final String getJPackageAppPath() {
        final String appPath = System.getProperty(APP_EXE_SYSPROP, "");
        return appPath.isEmpty() ? null : appPath;
    }

    private static final void addJPackageSystemPropertyArgument(final List<String> args, final String propName) {
        final String arg = getJPackageSystemPropertyArg(propName);
        if (arg != null)
            args.add(arg);
    }

    private static final String getJPackageSystemPropertyArg(final String propName) {
        final String val = System.getProperty(propName);
        if (val == null)
            return null;
        return "-D" + propName + "=" + val;
    }

    private static NativeLauncherFinder getNativeAppLauncher() {
        final OSFamily os = OSFamily.getInstance();
        final NativeLauncherFinder l;
        if (os.equals(OSFamily.Windows)) {
            l = new WinLauncherFinder();
        } else if (os.equals(OSFamily.Mac)) {
            l = new MacLauncherFinder();
        } else if (os instanceof Unix) {
            l = new UnixLauncherFinder();
        } else {
            l = UnknownLauncherFinder;
        }
        return l;
    }

    private static List<String> getNativeCommand(List<String> args) {
        final NativeLauncherFinder l = getNativeAppLauncher();
        return l.getAppPath() == null ? null : l.getCommand(args);
    }

    /**
     * Allow to find out if the running VM was launched using a native application.
     * 
     * @author Sylvain
     */
    private static abstract class NativeLauncherFinder {
        private final String cp, firstItem;

        public NativeLauncherFinder() {
            this.cp = ManagementFactory.getRuntimeMXBean().getClassPath();
            final int sepIndex = this.cp.indexOf(File.pathSeparatorChar);
            this.firstItem = sepIndex < 0 ? this.cp : this.cp.substring(0, sepIndex);
        }

        public final String getClassPath() {
            return this.cp;
        }

        public final String getFirstItem() {
            return this.firstItem;
        }

        /**
         * The path to the native application if any.
         * 
         * @return the path, <code>null</code> if no native application could be found.
         */
        public abstract String getAppPath();

        /**
         * The command to launch this application with the passed arguments.
         * 
         * @param args the program arguments.
         * @return the command.
         * @throws UnsupportedOperationException if {@link #getAppPath()} returns <code>null</code>.
         */
        public final List<String> getCommand(final List<String> args) throws UnsupportedOperationException {
            final String appPath = this.getAppPath();
            if (appPath == null)
                throw new UnsupportedOperationException();

            return getCommand(appPath, args);
        }

        protected List<String> getCommand(final String appPath, final List<String> args) {
            final List<String> command = new ArrayList<String>(4 + args.size());
            command.add(appPath);
            command.addAll(args);
            return command;
        }
    }

    private static class MacLauncherFinder extends NativeLauncherFinder {
        private static final String APP_EXT = ".app";
        // jpackage uses "Contents/app"
        private static final Pattern MAC_PATTERN = Pattern.compile(Pattern.quote(APP_EXT) + "/Contents/(Resources(/Java)?|app)/[^/]+\\.jar$");

        @Override
        public String getAppPath() {
            final Matcher matcher = MAC_PATTERN.matcher(this.getFirstItem());
            if (matcher.matches()) {
                final String appPath = getFirstItem().substring(0, matcher.start() + APP_EXT.length());
                final File contentsDir = new File(appPath, "Contents");
                if (new File(contentsDir, "Info.plist").isFile() && new File(contentsDir, "MacOS").isDirectory())
                    return appPath;
            }
            return null;
        }

        @Override
        protected List<String> getCommand(String appPath, List<String> args) {
            final List<String> command = new ArrayList<String>(4 + args.size());
            command.add("open");
            // since we restarting we need to launch a new instance of us
            command.add("-n");
            command.add(appPath);
            command.add("--args");
            command.addAll(args);
            return command;
        }
    }

    private static class WinLauncherFinder extends NativeLauncherFinder {
        @Override
        public String getAppPath() {
            // launch4j
            if (this.getFirstItem().endsWith(".exe"))
                return getFirstItem();
            else
                return null;
        }
    }

    private static class UnixLauncherFinder extends NativeLauncherFinder {

        private final String jpackageApp;

        public UnixLauncherFinder() {
            this.jpackageApp = getJPackageAppPath();
        }

        @Override
        public String getAppPath() {
            return this.jpackageApp;
        }
    }

    private static final NativeLauncherFinder UnknownLauncherFinder = new NativeLauncherFinder() {
        @Override
        public String getAppPath() {
            return null;
        }
    };

    public static final Process restart(final Class<?> mainClass, final String... args) throws IOException {
        return restart(mainClass, Arrays.asList(args));
    }

    public static final Process restart(final Redirect action, final Class<?> mainClass, final String... args) throws IOException {
        return restart(action, mainClass, Arrays.asList(args));
    }

    /**
     * Restart the VM. If this VM was launched using a native application (e.g. .exe or .app) then
     * this will be executed. Else the <code>mainClass</code> will be used.
     * 
     * @param mainClass the main() to use (if no native application was found).
     * @param args the program arguments to pass.
     * @return the new process, <code>null</code> if the program wasn't started.
     * @throws IOException if the VM couldn't be launched.
     * @see #NO_RESTART
     */
    public static final Process restart(final Class<?> mainClass, final List<String> args) throws IOException {
        return restart(ProcessStreams.DISCARD, mainClass, args);
    }

    public static final Process restart(final Redirect action, final Class<?> mainClass, final List<String> args) throws IOException {
        if (Boolean.getBoolean(NO_RESTART))
            return null;
        final File wd = FileUtils.getWD();
        final List<String> command = getNativeCommand(args);
        if (command != null) {
            return new ProcessBuilder(command).directory(wd).redirectErrorStream(true).redirectOutput(action).start();
        } else {
            try {
                mainClass.getMethod("main", String[].class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(mainClass + " doesn't containt a main()", e);
            }
            return new VMLauncher() {
                @Override
                protected File getWD() {
                    return wd;
                }

                @Override
                protected File getPropFile(String mainClass) {
                    return null;
                }

                @Override
                protected Redirect getStreamRedirect() {
                    return action;
                }
            }.launch(mainClass.getName(), args);
        }
    }

    public static final String ENV_VMARGS = "JAVA_VMARGS";
    public static final String PROPS_VMARGS = "VMARGS";
    public static final String ENV_PROGARGS = "JAVA_PROGARGS";

    // Don't split on spaces to avoid dealing with quotes or escapes : vmArgs=-Dfoo bar\t-DotherProp
    // Handle DOS, Mac and Unix newlines (and tabs).
    private static final Pattern NL = Pattern.compile("\\p{Cntrl}+");

    private File wd;

    public VMLauncher() {
        this.wd = null;
    }

    public final File getLauncherWD() {
        if (this.wd == null) {
            final File appDir = getJPackageAppDir();
            if (appDir == null) {
                final NativeLauncherFinder nativeAppLauncher = getNativeAppLauncher();
                final String appPath = nativeAppLauncher.getAppPath();
                if (appPath != null)
                    this.wd = new File(appPath).getAbsoluteFile().getParentFile();
                // when launched with -jar there's only one item
                else if (nativeAppLauncher.getFirstItem().equals(nativeAppLauncher.getClassPath()) && new File(nativeAppLauncher.getFirstItem()).isFile())
                    this.wd = new File(nativeAppLauncher.getFirstItem()).getParentFile();
                // support launch in an IDE
                else
                    this.wd = FileUtils.getWD();
            } else {
                this.wd = appDir;
            }
        }
        return this.wd;
    }

    private final List<String> split(String res) {
        res = res.trim();
        if (res.length() == 0) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(NL.split(res));
        }
    }

    private final List<String> getProp(final File propFile, final String propName) {
        return this.getProp(this.getProps(propFile), propName);
    }

    private final Properties getProps(final File propFile) {
        if (propFile != null && propFile.canRead()) {
            try {
                return PropertiesUtils.createFromFile(propFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new Properties();
    }

    private final List<String> getProp(final Properties props, final String propName) {
        String res = "";
        if (props != null) {
            res = props.getProperty(propName, res);
        }
        return split(res);
    }

    public final Process launch(final String mainClass) throws IOException {
        return this.launch(mainClass, Collections.<String> emptyList());
    }

    /**
     * Launch a new Java VM. This method will try to launch {@link #getJavaBinary() java} from the
     * same installation, if that fails it will use the system path for binaries. VM arguments can
     * be specified with :
     * <ol>
     * <li>the {@value #ENV_VMARGS} environment variable</li>
     * <li>the {@value #PROPS_VMARGS} property in {@link #getPropFile(String)}</li>
     * <li>the {@link #getVMArguments()} method</li>
     * </ol>
     * Program arguments :
     * <ol>
     * <li>the <code>progParams</code> parameter</li>
     * <li>the {@value #ENV_PROGARGS} environment variable</li>
     * </ol>
     * 
     * @param mainClass the main class.
     * @param progParams the program arguments for <code>mainClass</code>.
     * @return the new Process.
     * @throws IOException if the process couldn't be started.
     */
    public final Process launch(final String mainClass, final List<String> progParams) throws IOException {
        final boolean debug = Boolean.getBoolean("launcher.debug");
        final String javaBinary = getJavaBinary();
        final File sameJava = new File(System.getProperty("java.home"), "bin/" + javaBinary);
        // allow to know what binary (and thus java.home) was tested
        if (debug)
            System.err.println("sameJava : " + sameJava);
        final String java = sameJava.canExecute() ? sameJava.getAbsolutePath() : javaBinary;
        final File propFile = this.getPropFile(mainClass);
        final Properties props = this.getProps(propFile);
        if (debug)
            System.err.println("propFile : " + propFile);

        final List<String> command = new ArrayList<String>();
        command.add(java);

        if (this.enableRemoteDebug(props)) {
            command.add(RemoteDebugArgs.getArgs(props.getProperty("remoteDebugAddr")));
        }
        command.addAll(this.getVMArguments());

        // For java the last specified property wins. MAYBE concat properties whose names start with
        // PROPS_VMARGS, that way we could override just one of many e.g. VMARGS.garbageCollector.
        if (propFile != null) {
            final List<String> appProps = this.getProp(props, PROPS_VMARGS);
            command.addAll(appProps);
            if (debug) {
                System.err.println("VM arguments from " + propFile + " : " + appProps);
            }
            // Don't use Properties(defaults) constructor since we want to combine values.
            final File localFile = FileUtils.prependSuffix(propFile, "-local", PROPERTIES_EXT);
            final File userFile = new File(System.getProperty("user.home"), ".java/ilm/" + propFile.getName());
            for (final File f : Arrays.asList(localFile, userFile)) {
                final List<String> moreProps = this.getProp(f, PROPS_VMARGS);
                command.addAll(moreProps);
                if (debug) {
                    System.err.println("VM arguments from " + f + " : " + moreProps);
                }
            }
        }
        final String envVMArgs = System.getenv(ENV_VMARGS);
        if (envVMArgs != null)
            command.addAll(split(envVMArgs));
        // launched app may also need this context
        addJPackageSystemPropertyArgument(command, APPDIR_SYSPROP);
        addJPackageSystemPropertyArgument(command, APP_EXE_SYSPROP);

        command.add("-cp");
        command.add(getClassPath());
        command.add(mainClass);
        final String envProgArgs = System.getenv(ENV_PROGARGS);
        if (envProgArgs != null)
            command.addAll(split(envProgArgs));
        command.addAll(progParams);

        // inherit environment so that the next launch() can access the same variables
        final ProcessBuilder procBuilder = new ProcessBuilder(command).directory(getWD());
        this.modifyEnv(procBuilder.environment());
        if (debug) {
            System.err.println("Command line : " + procBuilder.command());
            System.err.println("Dir : " + procBuilder.directory());
            System.err.println("Std out and err :");
        }

        procBuilder.redirectErrorStream(true).redirectOutput(debug ? Redirect.INHERIT : this.getStreamRedirect());
        return procBuilder.start();
    }

    protected void modifyEnv(Map<String, String> environment) {
    }

    protected Redirect getStreamRedirect() {
        return ProcessStreams.DISCARD;
    }

    protected boolean enableRemoteDebug(Properties props) {
        final String prop = props.getProperty("remoteDebug");
        return prop == null ? remoteDebugDefault() : Boolean.parseBoolean(prop);
    }

    protected boolean remoteDebugDefault() {
        return false;
    }

    /**
     * The program to launch. This implementation returns <code>javaw</code> for Windows and
     * <code>java</code> for other OS.
     * 
     * @return the name of the binary.
     */
    protected String getJavaBinary() {
        return OSFamily.getInstance() == OSFamily.Windows ? "javaw" : "java";
    }

    protected List<String> getVMArguments() {
        return Arrays.asList("-Dfile.encoding=UTF-8", "-Xms100M", "-Xmx256M");
    }

    // by default in the same jar
    protected String getClassPath() {
        return ManagementFactory.getRuntimeMXBean().getClassPath();
    }

    // by default in the same directory
    protected File getWD() {
        return this.getLauncherWD();
    }

    protected File getPropFile(final String mainClass) {
        final String className = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        return new File(getWD(), className + PROPERTIES_EXT);
    }
}
