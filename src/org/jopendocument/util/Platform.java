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

import static org.jopendocument.util.DesktopEnvironment.cmdSubstitution;

import org.jopendocument.util.cc.ITransformer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * To abstract differences between platform for non java tasks.
 * 
 * @author Sylvain
 * @see DesktopEnvironment
 */
public abstract class Platform {

    public static final String PROCESS_ALLOW_AMBIGUOUS_COMMANDS = "jdk.lang.Process.allowAmbiguousCommands";

    private static final int PING_TIMEOUT = 250;

    public static final Platform getInstance() {
        final OSFamily os = OSFamily.getInstance();
        if (os == OSFamily.Windows) {
            return CYGWIN;
        } else if (os == OSFamily.FreeBSD) {
            return FREEBSD;
        } else if (os == OSFamily.Mac) {
            return MACOS;
        } else {
            return LINUX;
        }
    }

    public static class CannotPassArgumentException extends RuntimeException {
        private final String arg;

        private CannotPassArgumentException(String arg, String message) {
            super("Cannot pass " + arg + message);
            this.arg = arg;
        }

        public final String getArg() {
            return this.arg;
        }
    }

    public abstract boolean supportsPID();

    public abstract boolean isRunning(final int pid) throws IOException;

    public abstract String getPath(final File f);

    public String getPath(final Path p) {
        return this.getPath(p.toFile());
    }

    public String getProcessArg(final String arg) {
        return arg;
    }

    public final String getPID() throws IOException {
        // TODO remove reflection and getPreJava9PID() once on java 11
        try {
            final Class<?> phClass = Class.forName("java.lang.ProcessHandle");
            final Object ph = phClass.getMethod("current").invoke(null);
            return ((Number) phClass.getMethod("pid").invoke(ph)).toString();
        } catch (ClassNotFoundException e) {
            // fall back
        } catch (Exception e) {
            throw new IOException("Couldn't get PID", e);
        }
        return getPreJava9PID();
    }

    protected String getPreJava9PID() throws IOException {
        final Process p = this.eval("echo -n $PPID");
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            return reader.readLine();
        }
    }

    public final String tail(final File f, final int n) throws IOException {
        final Process p = Runtime.getRuntime().exec(new String[] { "tail", "-n" + n, this.getPath(f) });
        return cmdSubstitution(p);
    }

    protected abstract String getBash();

    /**
     * Create a symbolic link from f2 to f1. NOTE: the path from f2 to f1 is made relative (eg
     * lastLog -> ./allLogs/tuesdayLog).
     * 
     * @param f1 the destination of the link, eg "/dir/allLogs/tuesdayLog".
     * @param f2 the name of the link, eg "/dir/lastLog".
     * @throws IOException if an error occurs.
     */
    public final void ln_s(File f1, File f2) throws IOException {
        FileUtils.ln(f1, f2);
    }

    public boolean isSymLink(File f) throws IOException {
        return exitStatus(Runtime.getRuntime().exec(new String[] { "test", "-L", f.getAbsolutePath() })) == 0;
    }

    // see cygwin
    public File getNativeSymlinkFile(File dir) {
        return dir;
    }

    public abstract String readLink(File f) throws IOException;

    public final boolean exists(File f) throws IOException {
        return exitStatus(Runtime.getRuntime().exec(new String[] { "test", "-e", f.getAbsolutePath() })) == 0;
    }

    public final void append(File f1, File f2) throws IOException {
        final String c = "cat '" + f1.getAbsolutePath() + "' >> '" + f2.getAbsolutePath() + "'";
        this.waitForSuccess(this.eval(c), "append");
    }

    public Process cp_l(File src, File dest) throws IOException {
        return Runtime.getRuntime().exec(new String[] { "cp", "-prl", src.getAbsolutePath(), dest.getAbsolutePath() });
    }

    public final boolean ping(InetAddress host) throws IOException {
        return this.ping(host, PING_TIMEOUT);
    }

    /**
     * Test whether that address is reachable.
     * 
     * @param host the host to reach.
     * @param timeout the time, in milliseconds, before the call aborts.
     * @return <code>true</code> if the address is reachable.
     * @throws IOException if a network error occurs.
     */
    public abstract boolean ping(InetAddress host, final int timeout) throws IOException;

    public final PingBuilder createPingBuilder() {
        return new PingBuilder(this);
    }

    protected abstract PingResult ping(InetAddress host, final PingBuilder pingBuilder, final int routingTableIndex) throws IOException;

    protected abstract BigDecimal parsePingAverageRT(String statsLine);

    protected final PingResult ping(final String command, final int totalCount, int requiredCount) throws IOException {
        if (requiredCount <= 0)
            requiredCount = totalCount;
        // Keep errors out of cmdSubstitution() (e.g. "ping: sendto: Message too long" when
        // setDontFragment(true))
        final Process proc = evalPB(command).redirectErrorStream(false).start();

        // some programs won't write anything until they read everything
        proc.getOutputStream().close();
        final ByteArrayOutputStream out = new ByteArrayOutputStream(5 * 1024);
        final ByteArrayOutputStream err = new ByteArrayOutputStream(2 * 1024);
        try (final ProcessStreams streams = new ProcessStreams(proc)) {
            streams.start(out, err);
            streams.awaitTermination();
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't capture output of ping", e);
        }

        final String output = out.toString();
        try {
            this.waitForSuccess(proc, "ping");
            final List<String> countAndLastLine = StringUtils.splitIntoLines(output);
            if (countAndLastLine.size() != 2)
                throw new IllegalStateException("Not 2 lines in " + countAndLastLine);
            final int replied = Integer.parseInt(countAndLastLine.get(0));
            assert replied <= totalCount;
            final BigDecimal averageRTT = replied == 0 ? null : parsePingAverageRT(countAndLastLine.get(1).trim());
            return new PingResult(totalCount, replied, requiredCount, averageRTT);
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't use output :<<<\n" + output + "\n<<<\nerr:<<<\n" + err.toString() + "\n<<<", e);
        }
    }

    /**
     * Eval the passed string with bash.
     * 
     * @param s a bash script.
     * @return the created process.
     * @throws IOException If an I/O error occurs.
     */
    public final Process eval(String s) throws IOException {
        return evalPB(s).start();
    }

    public final ProcessBuilder evalPB(String s) throws IOException {
        return new ProcessBuilder(this.getBash(), "-c", s);
    }

    public final int exitStatus(Process p) {
        return this.exitStatus(p, null);
    }

    public final int exitStatus(Process p, final String name) {
        try {
            return p.waitFor();
        } catch (InterruptedException e) {
            throw new RTInterruptedException("Interrupted while waiting for" + (name == null ? "" : " '" + name + "'") + " process", e);
        }
    }

    public final void waitForSuccess(final Process p, final String name) {
        final int exitStatus = exitStatus(p, name);
        if (exitStatus != 0)
            throw new IllegalStateException(name + " unsuccessful : " + exitStatus);
    }

    public abstract boolean isAdmin() throws IOException;

    private static abstract class UnixPlatform extends Platform {

        @Override
        public boolean supportsPID() {
            return true;
        }

        @Override
        public final String getPreJava9PID() throws IOException {
            final String symlink = getSelfProcessSymlink();
            if (symlink == null)
                return super.getPreJava9PID();

            // readSymbolicLink() seems to faster than getCanonicalFile() or toRealPath().
            // Another way is using reflection for
            // ManagementFactory.getRuntimeMXBean().jvm.getProcessId()
            return Files.readSymbolicLink(Paths.get(symlink)).getFileName().toString();
        }

        protected abstract String getSelfProcessSymlink();

        public final boolean isRunning(final int pid) throws IOException {
            // --pid only works on Linux, -p also on Nexenta
            final Process p = Runtime.getRuntime().exec(new String[] { "ps", "-p", String.valueOf(pid) });
            return this.exitStatus(p) == 0;
        }

        @Override
        public String getPath(final File f) {
            return f.getPath();
        }

        @Override
        public String getPath(final Path f) {
            return f.toString();
        }

        @Override
        protected String getBash() {
            return "bash";
        }

        @Override
        public String readLink(File f) throws IOException {
            // --no-newline long form not supported on FreeBSD
            final Process p = Runtime.getRuntime().exec(new String[] { "readlink", "-n", f.getAbsolutePath() });
            return cmdSubstitution(p);
        }

        @Override
        public boolean ping(InetAddress host, final int timeout) throws IOException {
            try {
                return host.isReachable(timeout);
            } catch (SocketException e) {
                // On FreeBSD at least, if the destination is blocked by the firewall :
                // java.net.SocketException: Operation not permitted
                // at java.net.Inet4AddressImpl.isReachable0(Native Method)
                // at java.net.Inet4AddressImpl.isReachable(Inet4AddressImpl.java:70)
                Log.get().log(Level.FINER, "Swallow exception", e);
                return false;
            }
        }

        // Linux : rtt min/avg/max/mdev = 12.552/13.399/14.247/0.855 ms
        // FreeBSD : round-trip min/avg/max/stddev = 0.301/0.371/0.442/0.071 ms
        private static final Pattern PING_STATS_PATTERN = Pattern
                .compile("^\\p{Blank}*(?:rtt|round-trip)\\p{Blank}+min/avg/max/(?:mdev|stddev)\\p{Blank}+=\\p{Blank}+([0-9\\.]+)/([0-9\\.]+)/([0-9\\.]+)/([0-9\\.]+)\\p{Blank}+ms$");

        @Override
        protected BigDecimal parsePingAverageRT(String statsLine) {
            final Matcher m = PING_STATS_PATTERN.matcher(statsLine);
            if (!m.matches())
                throw new IllegalArgumentException("Not matching " + PING_STATS_PATTERN + " :\n" + statsLine);
            return new BigDecimal(m.group(2));
        }

        @Override
        public boolean isAdmin() throws IOException {
            // root is uid 0
            return cmdSubstitution(this.eval("id -u")).trim().equals("0");
        }
    }

    private static final Platform LINUX = new UnixPlatform() {

        @Override
        protected String getSelfProcessSymlink() {
            return "/proc/self";
        }

        @Override
        public PingResult ping(final InetAddress host, final PingBuilder pingBuilder, final int routingTableIndex) throws IOException {
            if (routingTableIndex > 0)
                throw new UnsupportedOperationException("On Linux, choosing a different routing table requires changing the system policy");
            final List<String> command = new ArrayList<String>(16);
            command.add("ping");
            final int totalCount = pingBuilder.getTotalCount();
            command.add("-c");
            command.add(String.valueOf(totalCount));

            if (pingBuilder.getWaitTime() > 0) {
                command.add("-W");
                final int timeInSeconds = pingBuilder.getWaitTime() / 1000;
                command.add(String.valueOf(Math.max(timeInSeconds, 1)));
            }

            command.add("-M");
            command.add(pingBuilder.isDontFragment() ? "do" : "dont");

            if (pingBuilder.getLength() > 0) {
                command.add("-s");
                command.add(String.valueOf(pingBuilder.getLength()));
            }
            if (pingBuilder.getTTL() > 0) {
                command.add("-t");
                command.add(String.valueOf(pingBuilder.getTTL()));
            }
            if (pingBuilder.getSourceAddress() != null) {
                command.add("-I");
                command.add(pingBuilder.getSourceAddress());
            }

            command.add(host.getHostAddress());

            return ping("out=$(" + CollectionUtils.join(command, " ") + ") ; grep -c ttl= <<< \"$out\" ; tail -n1 <<< \"$out\"", totalCount, pingBuilder.getRequiredReplies());
        }
    };

    private static final Platform FREEBSD = new UnixPlatform() {

        @Override
        protected String getSelfProcessSymlink() {
            return "/proc/curproc";
        }

        @Override
        public PingResult ping(final InetAddress host, final PingBuilder pingBuilder, final int routingTableIndex) throws IOException {
            final List<String> command = new ArrayList<String>(16);
            command.add("setfib");
            command.add(String.valueOf(routingTableIndex));
            command.add("ping");
            final int totalCount = pingBuilder.getTotalCount();
            command.add("-c");
            command.add(String.valueOf(totalCount));

            if (pingBuilder.getWaitTime() > 0) {
                command.add("-W");
                command.add(String.valueOf(pingBuilder.getWaitTime()));
            }

            if (pingBuilder.isDontFragment()) {
                command.add("-D");
            }
            if (pingBuilder.getLength() > 0) {
                command.add("-s");
                command.add(String.valueOf(pingBuilder.getLength()));
            }
            if (pingBuilder.getTTL() > 0) {
                command.add("-m");
                command.add(String.valueOf(pingBuilder.getTTL()));
            }
            if (pingBuilder.getSourceAddress() != null) {
                command.add("-S");
                command.add(pingBuilder.getSourceAddress());
            }

            command.add(host.getHostAddress());

            return ping("out=$(" + CollectionUtils.join(command, " ") + ") ; grep -c ttl= <<< \"$out\" ; tail -n1 <<< \"$out\"", totalCount, pingBuilder.getRequiredReplies());
        }
    };

    private static final Platform MACOS = new UnixPlatform() {
        @Override
        protected String getSelfProcessSymlink() {
            return null;
        }

        @Override
        protected PingResult ping(InetAddress host, PingBuilder pingBuilder, int routingTableIndex) throws IOException {
            return FREEBSD.ping(host, pingBuilder, routingTableIndex);
        }
    };

    // return 171 for "1.8.0_171"
    // return 11 for "11.0.11"
    public static final int getUpdateVersion(final char sep) {
        final String vers = System.getProperty("java.version");
        final int lastIndexOf = vers.lastIndexOf(sep);
        // e.g. "13"
        if (lastIndexOf < 0)
            return 0;
        return Integer.parseInt(vers.substring(lastIndexOf + 1));
    }

    public static abstract class WindowsPlatform extends Platform {
        // on Windows program themselves are required to parse the command line, thus a lot of them
        // do it differently, see "How Command Line Parameters Are Parsed"
        // https://daviddeley.com/autohotkey/parameters/parameters.htm

        static private final Pattern quotePatrn = Pattern.compile("([\\\\]*)\"");
        static private final Pattern endSlashPatrn = Pattern.compile("([\\\\]+)\\z");

        static private boolean needsQuoting(String s) {
            final int len = s.length();
            if (len == 0) // empty string have to be quoted
                return true;
            for (int i = 0; i < len; i++) {
                switch (s.charAt(i)) {
                case ' ':
                case '\t':
                case '"':
                    return true;
                }
            }
            return false;
        }

        // see http://bugs.sun.com/view_bug.do?bug_id=6468220
        // e.g. find.exe, choice.exe
        public String quoteParamForMsftC(String s) {
            if (!needsQuoting(s))
                return s;
            if (s.length() > 0) {
                // replace '(\*)"' by '$1$1\"', e.g. '\quote " \"' by '\quote \" \\\"'
                // $1 needed so that the backslash we add isn't escaped itself by a preceding
                // backslash
                s = quotePatrn.matcher(s).replaceAll("$1$1\\\\\"");
                // replace '(\*)\z' by '$1$1', e.g. 'foo\' by 'foo\\'
                // needed to not escape closing quote
                s = endSlashPatrn.matcher(s).replaceAll("$1$1");
            }
            return '"' + s + '"';
        }

        @Override
        public String getProcessArg(String arg) {
            return this.getProcessArg(arg, false);
        }

        public final String getScriptProcessArg(String arg) {
            return this.getProcessArg(arg, true);
        }

        private String getProcessArg(String arg, final boolean script) {
            // Perhaps should have one method for .exe and one for .cmd/.bat (ProcessImpl checks
            // with isShellFile() and isExe()).

            if (script && arg.indexOf('"') >= 0)
                throw new CannotPassArgumentException(arg, ", it contains a double quote which is always removed by wscript!SplitCommandLine()");

            /*
             * If has VERIFICATION_WIN32_SAFE && !allowAmbiguousCommands, then ProcessImpl behaves
             * almost correctly : the argument we pass arrives as-is to the process (so the caller
             * shouldn't quote, ProcessImpl will fail with "Malformed argument has embedded quote"
             * if a character was escaped). Otherwise ProcessImpl does nothing if arg begins and
             * ends with double quotes (it's the caller responsibility to correctly quote).
             */
            final boolean doubleQuote;
            // 1.8 or 11
            final String specVers = System.getProperty("java.specification.version");
            // 8 or 11
            final int jreFamilyVersion = Integer.parseInt(specVers.startsWith("1.") ? specVers.substring(2) : specVers);
            final String javaVendor = System.getProperty("java.vendor");
            // VERIFICATION_WIN32_SAFE thanks to https://nvd.nist.gov/vuln/detail/CVE-2019-2958
            // For Oracle and OpenJDK :
            // https://www.oracle.com/java/technologies/javase/8u231-relnotes.html#JDK-8221858
            // https://github.com/openjdk/jdk/commit/5a98b8cfb0cd4c5ce84746e9fa5e42a86d1b2d24#diff-71e1dde85b2937bbf08d0bfd9e8a2e3553c578a3f58907ba6f4f30ec350db184
            // For AdoptOpenJDK :
            // https://github.com/AdoptOpenJDK/openjdk-jdk11u/commit/0074850a15405a676a492d09e4955d4053966bbc#diff-71e1dde85b2937bbf08d0bfd9e8a2e3553c578a3f58907ba6f4f30ec350db184
            if (jreFamilyVersion >= 14 || (javaVendor.equals("AdoptOpenJDK") && (jreFamilyVersion == 11 && getUpdateVersion('.') >= 5 || jreFamilyVersion == 13 && getUpdateVersion('.') >= 1))
                    || (jreFamilyVersion == 8 && getUpdateVersion('_') >= 231)) {
                // copied from ProcessImpl
                final SecurityManager security = System.getSecurityManager();
                final String value = System.getProperty(PROCESS_ALLOW_AMBIGUOUS_COMMANDS, (security == null ? "true" : "false"));
                final boolean allowAmbiguousCommands = !"false".equalsIgnoreCase(value);
                if ((script || allowAmbiguousCommands) && arg.endsWith("\\")) {
                    // with script, we always do our own quoting since ProcessBuilder will double
                    // back slashes.
                    if (script || needsQuoting(arg)) {
                        throw new CannotPassArgumentException(arg,
                                ", if we'd double quote, then the string would end with 2 back slashes and 1 double quote, but ProcessImpl.unQuote() : 'not properly quoted, treat as unquoted'. So it would get quoted a second time. Try setting '"
                                        + PROCESS_ALLOW_AMBIGUOUS_COMMANDS + "' to 'false'.");
                    } else {
                        doubleQuote = false;
                    }
                } else if (!allowAmbiguousCommands && arg.length() >= 2 && arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
                    throw new CannotPassArgumentException(arg,
                            "\nif we pass it as-is then ProcessImpl will consider the string already quoted and needsEscaping() will return false, thus the quotes will be removed by CommandLineToArgvW()"
                                    + "\nif we quote it then ProcessImpl.needsEscaping() will throw 'Malformed argument has embedded quote'");
                } else {
                    doubleQuote = allowAmbiguousCommands;
                }
            } else {
                doubleQuote = true;
            }

            if (script)
                return '"' + arg + '"';
            else
                return doubleQuote ? quoteParamForMsftC(arg) : arg;
        }
    }

    private static final class CygwinPlatform extends WindowsPlatform {

        @Override
        public boolean supportsPID() {
            return false;
        }

        @Override
        public boolean isRunning(int pid) throws IOException {
            // PID TTY STIME COMMAND
            // 864 ? 09:27:57 \??\C:\WINDOWS\system32\winlogon.exe
            // so if we count lines, we should get 2
            final Process p = this.eval("export PATH=$PATH:/usr/bin ; test $(ps -sW -p " + pid + " | wc -l) -eq 2 ");
            return this.exitStatus(p) == 0;
        }

        @Override
        protected String getBash() {
            // We used to specify the full path here, but this needed to be configurable (e.g.
            // cygwin 32 or 64 bit), plus all other programs are required to be on the PATH
            return "bash.exe";
        }

        @Override
        public String readLink(File f) throws IOException {
            // windows format to be able to use File
            final Process p = Runtime.getRuntime().exec(new String[] { "readshortcut.exe", "-w", f.getAbsolutePath() });
            return cmdSubstitution(p).trim();
        }

        @Override
        public String getPath(File f) {
            return toCygwinPath(f);
        }

        @Override
        public boolean ping(InetAddress host, final int timeout) throws IOException {
            // windows implem of isReachable() is buggy
            // see http://bordet.blogspot.com/2006/07/icmp-and-inetaddressisreachable.html
            final int exit = this.exitStatus(Runtime.getRuntime().exec("ping -n 1 -w " + timeout + " " + host.getHostAddress()), "ping");
            return exit == 0;
        }

        @Override
        public PingResult ping(final InetAddress host, final PingBuilder pingBuilder, final int routingTableIndex) throws IOException {
            if (routingTableIndex > 0)
                throw new UnsupportedOperationException("Only one routing table on Windows");
            final List<String> command = new ArrayList<String>(16);
            command.add("ping");
            final int totalCount = pingBuilder.getTotalCount();
            command.add("-n");
            command.add(String.valueOf(totalCount));

            if (pingBuilder.getWaitTime() > 0) {
                command.add("-w");
                command.add(String.valueOf(pingBuilder.getWaitTime()));
            }

            if (pingBuilder.isDontFragment()) {
                command.add("-f");
            }
            if (pingBuilder.getLength() > 0) {
                command.add("-l");
                command.add(String.valueOf(pingBuilder.getLength()));
            }
            if (pingBuilder.getTTL() > 0) {
                command.add("-i");
                command.add(String.valueOf(pingBuilder.getTTL()));
            }
            if (pingBuilder.getSourceAddress() != null) {
                command.add("-S");
                command.add(pingBuilder.getSourceAddress());
            }

            command.add(host.getHostAddress());

            // can't use local variable : never could work out newlines problem
            // see https://cygwin.com/ml/cygwin-announce/2011-02/msg00027.html
            return ping("tmpF=$(mktemp) && " + CollectionUtils.join(command, " ") + " > $tmpF ; grep -c \"TTL=\" < $tmpF ; tail -n1 $tmpF; rm \"$tmpF\"", totalCount, pingBuilder.getRequiredReplies());
        }

        // Minimum = 35ms, Maximum = 36ms, Moyenne = 35ms
        private static final Pattern PING_STATS_PATTERN = Pattern.compile("^\\p{Blank}*\\p{Alpha}+ = ([0-9\\.]+)ms, \\p{Alpha}+ = ([0-9\\.]+)ms, \\p{Alpha}+ = ([0-9\\.]+)ms$");

        @Override
        protected BigDecimal parsePingAverageRT(String statsLine) {
            final Matcher m = PING_STATS_PATTERN.matcher(statsLine);
            if (!m.matches())
                throw new IllegalArgumentException("Not matching " + PING_STATS_PATTERN + " : " + statsLine);
            return new BigDecimal(m.group(3));
        }

        @Override
        public boolean isAdmin() throws IOException {
            // SID administrators S-1-5-32-544 or S-1-5-114
            return this.exitStatus(this.eval("id -G | egrep '\\b(544|114)\\b'")) == 0;
        }

        @Override
        public boolean isSymLink(File f) throws IOException {
            // links created with "ln" can loose their "test -L" status over a copy (eg between two
            // Eclipse workspaces), so check with filename
            return getNativeSymlinkFile(f).exists() || super.isSymLink(f);
        }

        // when cygwin does "ln -s f link" it actually creates "link.lnk"
        @Override
        public File getNativeSymlinkFile(File dir) {
            return FileUtils.addSuffix(dir, ".lnk");
        }
    };

    private static final Platform CYGWIN = new CygwinPlatform();

    public static String toCygwinPath(File dir) {
        final List<File> ancestors = getAncestors(dir);

        final String root = ancestors.get(0).getPath();
        final List<File> rest = ancestors.subList(1, ancestors.size());
        return "/cygdrive/" + root.charAt(0) + "/" + CollectionUtils.join(rest, "/", new ITransformer<File, String>() {
            @Override
            public String transformChecked(File f) {
                return f.getName();
            }
        });
    }

    public static List<File> getAncestors(File f) {
        final File abs = f.getAbsoluteFile();
        File current = abs;
        final List<File> res = new ArrayList<File>();

        while (current != null) {
            res.add(0, current);
            current = current.getParentFile();
        }
        return res;
    }

    public static final class PingResult {

        private final int wantedCount, repliesCount, requiredReplies;
        private final BigDecimal averageRTT;

        private PingResult(final int wantedCount, final int repliesCount, final int requiredReplies, final BigDecimal averageRTT) {
            this.wantedCount = wantedCount;
            this.repliesCount = repliesCount;
            this.requiredReplies = requiredReplies;
            this.averageRTT = averageRTT;
        }

        public final int getRepliesCount() {
            return this.repliesCount;
        }

        public final int getRequiredReplies() {
            return this.requiredReplies;
        }

        public final boolean hasRequiredReplies() {
            return this.hasEnoughReplies(this.getRequiredReplies());
        }

        public final boolean hasEnoughReplies(final int min) {
            return this.getRepliesCount() >= min;
        }

        public final BigDecimal getAverageRTT() {
            return this.averageRTT;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " " + this.getRepliesCount() + "/" + this.wantedCount + " reply(ies), average RTT : " + this.getAverageRTT() + " ms";
        }
    }

    public static final class PingBuilder {

        private final Platform platform;

        // in milliseconds
        private int waitTime = 4000;
        private boolean dontFragment = false;
        private int length = -1;
        private String srcAddr = null;
        private int ttl = -1;
        private int totalCount = 4;
        private int requiredReplies = -1;

        PingBuilder(final Platform p) {
            this.platform = p;
        }

        public final PingBuilder setWaitTime(final int waitTime) {
            this.waitTime = waitTime;
            return this;
        }

        public final int getWaitTime() {
            return this.waitTime;
        }

        public final boolean isDontFragment() {
            return this.dontFragment;
        }

        public final PingBuilder setDontFragment(boolean dontFragment) {
            this.dontFragment = dontFragment;
            return this;
        }

        public final int getLength() {
            return this.length;
        }

        public final PingBuilder setLength(int length) {
            this.length = length;
            return this;
        }

        public String getSourceAddress() {
            return this.srcAddr;
        }

        public PingBuilder setSourceAddress(String srcAddr) {
            this.srcAddr = srcAddr;
            return this;
        }

        public final int getTTL() {
            return this.ttl;
        }

        public final PingBuilder setTTL(int ttl) {
            this.ttl = ttl;
            return this;
        }

        public final int getTotalCount() {
            return this.totalCount;
        }

        public final PingBuilder setTotalCount(int totalCount) {
            if (totalCount <= 0)
                throw new IllegalArgumentException("Negative count : " + totalCount);
            this.totalCount = totalCount;
            return this;
        }

        public final int getRequiredReplies() {
            return this.requiredReplies;
        }

        public final PingBuilder setRequiredReplies(int requiredReplies) {
            this.requiredReplies = requiredReplies;
            return this;
        }

        public final boolean isAlive(final InetAddress host) throws IOException {
            return this.isAlive(host, 0);
        }

        public final boolean isAlive(final InetAddress host, final int routingTableIndex) throws IOException {
            return execute(host, routingTableIndex).hasRequiredReplies();
        }

        public PingResult execute(final InetAddress host) throws IOException {
            return execute(host, 0);
        }

        public PingResult execute(final InetAddress host, final int routingTableIndex) throws IOException {
            return this.platform.ping(host, this, routingTableIndex);
        }
    }

    public static final class Ping {
        public static void main(String[] args) throws IOException {
            final int totalCount = Integer.parseInt(System.getProperty("count", "4"));
            final String srcAddr = System.getProperty("srcAddr");
            System.out.println(Platform.getInstance().createPingBuilder().setTotalCount(totalCount).setSourceAddress(srcAddr).execute(InetAddress.getByName(args[0])));
        }
    }
}
