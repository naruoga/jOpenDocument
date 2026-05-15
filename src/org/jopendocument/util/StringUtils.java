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

/*
 * Créé le 3 mars 2005
 */
package org.jopendocument.util;

import java.awt.FontMetrics;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.icu.lang.UCharacter;

/**
 * @author Sylvain CUAZ
 */
public class StringUtils {

    // required encoding see Charset
    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final Charset UTF16 = Charset.forName("UTF-16");
    public static final Charset ASCII = Charset.forName("US-ASCII");
    public static final Charset ISO8859_1 = Charset.forName("ISO-8859-1");
    // included in rt.jar see
    // http://docs.oracle.com/javase/7/docs/technotes/guides/intl/encoding.doc.html
    public static final Charset ISO8859_15 = Charset.forName("ISO-8859-15");
    public static final Charset Cp1252 = Charset.forName("Cp1252");
    public static final Charset Cp850 = Charset.forName("Cp850");

    public static final char BOM = '\ufeff';

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Retourne la chaine avec la première lettre en majuscule et le reste en minuscule.
     * 
     * @param s la chaîne à transformer.
     * @return la chaine avec la première lettre en majuscule et le reste en minuscule.
     */
    public static String firstUpThenLow(String s) {
        if (s.length() == 0) {
            return s;
        }
        if (s.length() == 1) {
            return s.toUpperCase();
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public static String firstUp(String s) {
        if (s.length() == 0) {
            return s;
        }
        if (s.length() == 1) {
            return s.toUpperCase();
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    static public abstract class Shortener {

        private final int hashSize;
        private final int hashPartSize;
        private final String prefix;
        private final String suffix;
        private final int minStringLength;

        protected Shortener(int hashSize, String prefix, String suffix, int minCharsBeforeAndAfter) {
            super();
            this.hashSize = hashSize;
            this.prefix = prefix;
            this.suffix = suffix;
            this.hashPartSize = this.hashSize + this.prefix.length() + this.suffix.length();
            if (minCharsBeforeAndAfter < 1)
                throw new IllegalArgumentException("minCharsBeforeAndAfter must be at least 1: " + minCharsBeforeAndAfter);
            this.minStringLength = this.hashPartSize + minCharsBeforeAndAfter * 2;
        }

        public final int getMinStringLength() {
            return this.minStringLength;
        }

        public final String getBoundedLengthString(final String s, final int maxLength) {
            // don't test first for s.length, it's more predictable
            // (otherwise boundedString("a", 2) would succeed)
            if (maxLength < this.getMinStringLength())
                throw new IllegalArgumentException("Maximum too low : " + maxLength + "<" + getMinStringLength());
            if (s.length() <= maxLength)
                return s;
            else
                return this.shorten(s, maxLength);
        }

        final String shorten(final String s, final int maxLength) {
            assert s.length() >= this.getMinStringLength();
            final int toRemoveLength = s.length() - maxLength + this.hashPartSize;
            // remove the middle part of encoded
            final int toRemoveStartIndex = s.length() / 2 - toRemoveLength / 2;
            final String toHash = s.substring(toRemoveStartIndex, toRemoveStartIndex + toRemoveLength);

            final String hash = shorten(toHash);
            assert this.hashSize == hash.length();

            final String res = s.substring(0, toRemoveStartIndex) + this.prefix + hash + this.suffix + s.substring(toRemoveStartIndex + toRemoveLength);
            assert res.length() == maxLength;
            return res;
        }

        protected abstract String shorten(String s);

        static public final Shortener Ellipsis = new Shortener(1, "", "", 1) {
            @Override
            protected String shorten(String s) {
                return "…";
            }
        };

        // String.hashCode() is an int written in hex
        static public final Shortener JavaHashCode = new Shortener(Integer.SIZE / 8 * 2, "#", "#", 3) {
            @Override
            protected String shorten(String s) {
                return MessageDigestUtils.asHex(MessageDigestUtils.int2bytes(s.hashCode()));
            }
        };

        // 128 bits written in hex
        static public final Shortener MD5 = new Shortener(128 / 8 * 2, "#", "#", 11) {
            @Override
            protected String shorten(String s) {
                return MessageDigestUtils.getHashString(MessageDigestUtils.getMD5(), s.getBytes(UTF8));
            }
        };

        // order descendant by getMinStringLength()
        static final Shortener[] ORDERED = new Shortener[] { MD5, JavaHashCode, Ellipsis };
    }

    /**
     * The minimum value for {@link #getBoundedLengthString(String, int)}.
     * 
     * @return the minimum value for <code>maxLength</code>.
     */
    public static final int getLeastMaximum() {
        return Shortener.ORDERED[Shortener.ORDERED.length - 1].getMinStringLength();
    }

    private static final Shortener getShortener(final int l) {
        for (final Shortener sh : Shortener.ORDERED) {
            if (l >= sh.getMinStringLength())
                return sh;
        }
        return null;
    }

    /**
     * Return a string built from <code>s</code> that is at most <code>maxLength</code> long.
     * 
     * @param s the string to bound.
     * @param maxLength the maximum length the result must have.
     * @return a string built from <code>s</code>.
     * @throws IllegalArgumentException if <code>maxLength</code> is too small.
     * @see #getLeastMaximum()
     * @see Shortener#getBoundedLengthString(String, int)
     */
    public static final String getBoundedLengthString(final String s, final int maxLength) throws IllegalArgumentException {
        // don't test first for s.length, it's more predictable
        // (otherwise boundedString("a", 2) would succeed)
        if (maxLength < getLeastMaximum())
            throw new IllegalArgumentException("Maximum too low : " + maxLength + "<" + getLeastMaximum());

        final String res;
        if (s.length() <= maxLength) {
            res = s;
        } else {
            // use maxLength to choose the shortener since it's generally a constant
            // and thus the strings returned by this method have the same pattern
            res = getShortener(maxLength).shorten(s, maxLength);
        }
        return res;
    }

    static public enum Side {
        LEFT, RIGHT
    }

    public static String getFixedWidthString(final String s, final int width, final Side align) {
        return getFixedWidthString(s, width, align, false);
    }

    public static String getFixedWidthString(final String s, final int width, final Side align, final boolean allowShorten) {
        final int length = s.length();
        final String res;
        if (length == width) {
            res = s;
        } else if (length < width) {
            // we already tested length, so no need to allow shorten
            res = appendFixedWidthString(new StringBuilder(width), s, width, align, ' ', false).toString();
        } else {
            res = getTooWideString(s, width, allowShorten);
        }
        assert res.length() == width;
        return res;
    }

    private static String getTooWideString(final String s, final int width, final boolean allowShorten) {
        assert s.length() > width;
        if (!allowShorten)
            throw new IllegalArgumentException("Too wide : " + s.length() + " > " + width);
        return getBoundedLengthString(s, width);
    }

    public static StringBuilder appendFixedWidthString(final StringBuilder sb, final String s, final int width, final Side align, final char filler, final boolean allowShorten) {
        final int origBuilderLen = sb.length();
        final int length = s.length();
        if (length <= width) {
            sb.ensureCapacity(origBuilderLen + width);
            if (align == Side.LEFT)
                sb.append(s);
            for (int i = length; i < width; i++) {
                sb.append(filler);
            }
            if (align == Side.RIGHT)
                sb.append(s);
        } else {
            sb.append(getTooWideString(s, width, allowShorten));
        }
        assert sb.length() == origBuilderLen + width;
        return sb;
    }

    public static final List<String> fastSplit(final String string, final char sep) {
        final List<String> l = new ArrayList<String>();
        final int length = string.length();
        final char[] cars = string.toCharArray();
        int rfirst = 0;

        for (int i = 0; i < length; i++) {
            if (cars[i] == sep) {
                l.add(new String(cars, rfirst, i - rfirst));
                rfirst = i + 1;
            }
        }

        if (rfirst < length) {
            l.add(new String(cars, rfirst, length - rfirst));
        }
        return l;
    }

    public static final List<String> fastSplitTrimmed(final String string, final char sep) {
        final List<String> l = new ArrayList<String>();
        final int length = string.length();
        final char[] cars = string.toCharArray();
        int rfirst = 0;

        for (int i = 0; i < length; i++) {
            if (cars[i] == sep) {
                l.add(new String(cars, rfirst, i - rfirst).trim());
                rfirst = i + 1;
            }
        }

        if (rfirst < length) {
            l.add(new String(cars, rfirst, length - rfirst).trim());
        }
        return l;
    }

    /**
     * Split une string s tous les nbCharMaxLine
     * 
     * @param s
     * @param nbCharMaxLine
     * @return
     */
    public static String splitString(String s, int nbCharMaxLine) {

        if (s == null) {
            return s;
        }

        if (s.trim().length() < nbCharMaxLine) {
            return s;
        }
        StringBuffer lastString = new StringBuffer();
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {

            if (lastString.length() == nbCharMaxLine) {
                int esp = lastString.lastIndexOf(" ");
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append("\n");
                }
                if (esp > 0) {
                    result.append(lastString.substring(0, esp).toString().trim());
                    lastString = new StringBuffer(lastString.substring(esp, lastString.length()));
                } else {
                    result.append(lastString.toString().trim());
                    lastString = new StringBuffer();
                }
                result.append("\n");
            }

            char charAt = s.charAt(i);
            if (charAt == '\n') {
                lastString.append(charAt);
                result.append(lastString);
                lastString = new StringBuffer();
            } else {
                lastString.append(charAt);
            }
        }

        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
            result.append("\n");
        }

        result.append(lastString.toString().trim());

        return result.toString();
    }

    static public int firstIndexOf(final String s, final char[] chars) {
        return firstIndexOf(s, 0, chars);
    }

    static public int firstIndexOf(final String s, final int offset, final char[] chars) {
        int res = -1;
        for (final char c : chars) {
            final int index = s.indexOf(c, offset);
            if (index >= 0 && (res == -1 || index < res))
                res = index;
        }
        return res;
    }

    static private final Pattern singleQuote = Pattern.compile("'", Pattern.LITERAL);
    static public final Pattern SINGLE_QUOTED_PATTERN = Pattern.compile("'(('')|[^'])*'");
    static private final Pattern twoSingleQuote = Pattern.compile("''", Pattern.LITERAL);

    /**
     * Quote a string with single quotes ( e.g. for {@link SimpleDateFormat}).
     * 
     * @param s an arbitrary string, eg "salut\ l'ami".
     * @return the quoted form, eg "'salut\ l''ami'".
     */
    static public final String singleQuote(String s) {
        if (s.isEmpty())
            return "''";
        else
            return "'" + singleQuote.matcher(s).replaceAll("''") + "'";
    }

    static public final String unSingleQuote(String s) {
        if (!SINGLE_QUOTED_PATTERN.matcher(s).matches())
            throw new IllegalArgumentException("Invalid quoted string " + s);
        return unSingleQuoteUnsafe(s);
    }

    private static String unSingleQuoteUnsafe(String s) {
        return twoSingleQuote.matcher(s.substring(1, s.length() - 1)).replaceAll("'");
    }

    static public final String unSingleQuote(Matcher m) {
        if (m.pattern() != SINGLE_QUOTED_PATTERN)
            throw new IllegalArgumentException("Matcher not from SINGLE_QUOTED_PATTERN : " + m);
        return unSingleQuoteUnsafe(m.group());
    }

    static private final Pattern quotePatrn = Pattern.compile("\"", Pattern.LITERAL);
    static private final Pattern slashPatrn = Pattern.compile("(\\\\+)");

    static public String doubleQuote(String s) {
        return doubleQuote(s, true);
    }

    static public String doubleQuote(String s, final boolean escapeEscapeChar) {
        // http://developer.apple.com/library/mac/#documentation/applescript/conceptual/applescriptlangguide/reference/ASLR_classes.html#//apple_ref/doc/uid/TP40000983-CH1g-SW6
        // http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.5
        // https://developer.mozilla.org/en/JavaScript/Guide/Values%2C_Variables%2C_and_Literals#Escaping_characters
        if (s.length() > 0) {
            if (escapeEscapeChar)
                s = slashPatrn.matcher(s).replaceAll("$1$1");
            s = quotePatrn.matcher(s).replaceAll("\\\\\"");
        }
        return '"' + s + '"';
    }

    /**
     * Unquote a double quoted string.
     * 
     * @param s the string to unquote, e.g. "foo\\bar".
     * @return the unquoted form, e.g. foo\bar.
     * @throws IllegalArgumentException if the string is not quoted, or if there's some extra
     *         content at the end.
     */
    static public String unDoubleQuote(String s) {
        final Tuple2<String, Integer> res = unDoubleQuote(s, 0);
        if (res.get1().intValue() != s.length())
            throw new IllegalArgumentException("Extra content at the end : " + s.substring(res.get1()));
        return res.get0();
    }

    /**
     * Unquote part of a double quoted string.
     * 
     * @param s the string to unquote, e.g. pre"foo\\bar"post.
     * @param offset the start index of the quotes, e.g. 3.
     * @return the unquoted form and the index after the end quote, e.g. foo\bar and 13.
     * @throws IllegalArgumentException if the string is not quoted.
     */
    static public Tuple2<String, Integer> unDoubleQuote(String s, int offset) {
        if (s.charAt(offset) != '"')
            throw new IllegalArgumentException("Expected quote but got : " + s.charAt(offset));
        final int l = s.length();
        if (offset + 1 < l && s.charAt(offset + 1) == '"')
            return Tuple2.create("", offset + 2);

        offset++;
        final char[] chars = new char[] { '"', '\\' };
        final StringBuilder sb = new StringBuilder(512);
        boolean foundEnd = false;
        while (offset < l && !foundEnd) {
            final int index = firstIndexOf(s, offset, chars);
            if (index < 0)
                throw new IllegalArgumentException("End quote not found after " + offset);
            sb.append(s.substring(offset, index));
            if (s.charAt(index) == '"') {
                offset = index + 1;
                foundEnd = true;
            } else {
                assert s.charAt(index) == '\\';
                sb.append(s.charAt(index + 1));
                offset = index + 2;
            }
        }
        if (!foundEnd)
            throw new IllegalArgumentException("End quote not found after " + offset);
        return Tuple2.create(sb.toString(), offset);
    }

    public static final class Escaper {

        // eg '
        private final char esc;

        // eg { '=> S, " => D}
        private final Map<Character, Character> substitution;
        private final Map<Character, Character> inv;

        /**
         * A new escaper that will have <code>esc</code> as escape character.
         * 
         * @param esc the escape character, eg '
         * @param name the character that will be appended to <code>esc</code>, eg with S all
         *        occurrences of ' will be replaced by 'S
         */
        public Escaper(char esc, char name) {
            super();
            this.esc = esc;
            this.substitution = new LinkedHashMap<Character, Character>();
            this.inv = new HashMap<Character, Character>();
            this.add(esc, name);
        }

        public Escaper add(char toRemove, char escapedName) {
            if (this.inv.containsKey(escapedName))
                throw new IllegalArgumentException(escapedName + " already replaces " + this.inv.get(escapedName));
            this.substitution.put(toRemove, escapedName);
            this.inv.put(escapedName, toRemove);
            return this;
        }

        public final Set<Character> getEscapedChars() {
            final Set<Character> res = new HashSet<Character>(this.substitution.keySet());
            res.remove(this.esc);
            return res;
        }

        /**
         * Escape <code>s</code>, so that the resulting string has none of
         * {@link #getEscapedChars()}.
         * 
         * @param s a string to escape.
         * @return the escaped form.
         */
        public final String escape(String s) {
            String res = s;
            // this.esc en premier
            for (final Character toEsc : this.substitution.keySet()) {
                // use Pattern.LITERAL to avoid interpretion
                res = res.replace(toEsc + "", getEscaped(toEsc));
            }
            return res;
        }

        private String getEscaped(final Character toEsc) {
            return this.esc + "" + this.substitution.get(toEsc);
        }

        public final String unescape(String escaped) {
            String res = escaped;
            final List<Character> toEscs = new ArrayList<Character>(this.substitution.keySet());
            Collections.reverse(toEscs);
            for (final Character toEsc : toEscs) {
                res = res.replaceAll(getEscaped(toEsc), toEsc + "");
            }
            return res;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Escaper) {
                final Escaper o = (Escaper) obj;
                return this.esc == o.esc && this.substitution.equals(o.substitution);
            } else
                return false;
        }

        @Override
        public int hashCode() {
            return this.esc + this.substitution.hashCode();
        }
    }

    public static String rightAlign(String s, int width) {
        String r = s;
        int n = width - s.length();
        for (int i = 0; i < n; i++) {
            r = ' ' + r;
        }
        return r;
    }

    public static String leftAlign(String s, int width) {
        String r = s;
        int n = width - s.length();
        for (int i = 0; i < n; i++) {
            r += ' ';
        }
        return r;
    }

    public static String trim(final String s, final boolean leading) {
        // from String.trim()
        int end = s.length();
        int st = 0;

        if (leading) {
            while ((st < end) && (s.charAt(st) <= ' ')) {
                st++;
            }
        } else {
            while ((st < end) && (s.charAt(end - 1) <= ' ')) {
                end--;
            }
        }
        return ((st > 0) || (end < s.length())) ? s.substring(st, end) : s;
    }

    public static String limitLength(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength);
    }

    public static String removeAllSpaces(String text) {
        final int length = text.length();
        final StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c <= ' ' && c != 160) {
                // remove non printable chars
                // spaces
                // non breakable space (160)
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static String removeNonDecimalChars(String text) {
        final int length = text.length();
        final StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '+' || c == '-') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static BigDecimal getBigDecimalFromUserText(String text) {
        text = text.trim();
        if (text.isEmpty() || text.equals("-")) {
            return BigDecimal.ZERO;
        }
        text = removeNonDecimalChars(text);
        BigDecimal result = null;
        try {
            result = new BigDecimal(text);
        } catch (Exception e) {
            Log.get().info(text + " is not a valid decimal");
        }
        return result;
    }

    /**
     * Returns an array of strings, one for each line in the string after it has been wrapped to fit
     * lines of <var>maxWidth</var>. Lines end with any of cr, lf, or cr lf. A line ending at the
     * end of the string will not output a further, empty string.
     * <p>
     * This code assumes <var>str</var> is not <code>null</code>.
     * 
     * @param str the string to split
     * @param fm needed for string width calculations
     * @param maxWidth the max line width, in points
     * @return a non-empty list of strings
     */
    public static List<String> wrap(String str, FontMetrics fm, int maxWidth) {
        List<String> lines = splitIntoLines(str);
        if (lines.size() == 0)
            return lines;

        List<String> strings = new ArrayList<String>();
        for (Iterator<String> iter = lines.iterator(); iter.hasNext();) {
            wrapLineInto(iter.next(), strings, fm, maxWidth);
        }
        return strings;
    }

    /**
     * Given a line of text and font metrics information, wrap the line and add the new line(s) to
     * <var>list</var>.
     * 
     * @param line a line of text
     * @param list an output list of strings
     * @param fm font metrics
     * @param maxWidth maximum width of the line(s)
     */
    public static void wrapLineInto(String line, List<String> list, FontMetrics fm, int maxWidth) {
        int len = line.length();
        int width;
        while (len > 0 && (width = fm.stringWidth(line)) > maxWidth) {
            // Guess where to split the line. Look for the next space before
            // or after the guess.
            int guess = len * maxWidth / width;
            String before = line.substring(0, guess).trim();

            width = fm.stringWidth(before);
            int pos;
            if (width > maxWidth) // Too long
                pos = findBreakBefore(line, guess);
            else { // Too short or possibly just right
                pos = findBreakAfter(line, guess);
                if (pos != -1) { // Make sure this doesn't make us too long
                    before = line.substring(0, pos).trim();
                    if (fm.stringWidth(before) > maxWidth)
                        pos = findBreakBefore(line, guess);
                }
            }
            if (pos == -1)
                pos = guess; // Split in the middle of the word

            list.add(line.substring(0, pos).trim());
            line = line.substring(pos).trim();
            len = line.length();
        }
        if (len > 0) {
            list.add(line);
        }
    }

    /**
     * Returns the index of the first whitespace character or '-' in <var>line</var> that is at or
     * before <var>start</var>. Returns -1 if no such character is found.
     * 
     * @param line a string
     * @param start where to star looking
     */
    public static int findBreakBefore(String line, int start) {
        for (int i = start; i >= 0; --i) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || c == '-')
                return i;
        }
        return -1;
    }

    /**
     * Returns the index of the first whitespace character or '-' in <var>line</var> that is at or
     * after <var>start</var>. Returns -1 if no such character is found.
     * 
     * @param line a string
     * @param start where to star looking
     */
    public static int findBreakAfter(String line, int start) {
        int len = line.length();
        for (int i = start; i < len; ++i) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || c == '-')
                return i;
        }
        return -1;
    }

    /**
     * Returns an array of strings, one for each line in the string. Lines end with any of cr, lf,
     * or cr lf. A line ending at the end of the string will not output a further, empty string.
     * <p>
     * This code assumes <var>str</var> is not <code>null</code>.
     * 
     * @param str the string to split
     * @return a non-empty list of strings
     */
    public static List<String> splitIntoLines(String str) {
        List<String> strings = new ArrayList<String>();

        int len = str.length();
        if (len == 0) {
            strings.add("");
            return strings;
        }

        int lineStart = 0;

        for (int i = 0; i < len; ++i) {
            char c = str.charAt(i);
            if (c == '\r') {
                int newlineLength = 1;
                if ((i + 1) < len && str.charAt(i + 1) == '\n')
                    newlineLength = 2;
                strings.add(str.substring(lineStart, i));
                lineStart = i + newlineLength;
                if (newlineLength == 2) // skip \n next time through loop
                    ++i;
            } else if (c == '\n') {
                strings.add(str.substring(lineStart, i));
                lineStart = i + 1;
            }
        }
        if (lineStart < len)
            strings.add(str.substring(lineStart));

        return strings;
    }

    /**
     * Convert a byte array to its hexa representation (uppercase)
     * 
     * @param bytes to converts
     * @param size of the byte array to use
     * @return the hexa representation
     */
    public static String bytesToHexString(byte[] bytes, int length) {
        char[] hexChars = new char[length * 2];
        for (int j = 0; j < length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String charToHex(char c) {
        int v = c & 0xFF;
        char[] hexChars = new char[2];
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];
        return new String(hexChars);
    }

    /**
     * Convert a byte array to its hexa representation (uppercase)
     * 
     * @param bytes to converts
     * @return the hexa representation
     */
    public static String bytesToHexString(byte[] bytes) {
        return bytesToHexString(bytes, bytes.length);
    }

    /**
     * Whether the parameter is empty.
     * 
     * @param s the string to test.
     * @return <code>true</code> if <code>null</code> or {@link String#isEmpty() empty}.
     */
    public static boolean isEmpty(final String s) {
        return isEmpty(s, false);
    }

    public static boolean isEmpty(final String s, final boolean trim) {
        return s == null || (trim ? s.trim() : s).isEmpty();
    }

    /**
     * Return the first value that is non-empty.
     * 
     * @param values values to test for emptiness.
     * @return the first value that is neither <code>null</code> nor {@link String#isEmpty() empty},
     *         <code>null</code> if none.
     */
    public static String coalesce(String... values) {
        return coalesce(false, values);
    }

    public static String coalesce(final boolean trim, String... values) {
        for (final String s : values) {
            if (!isEmpty(s, trim))
                return s;
        }
        return null;
    }

    public static String toAsciiString(String str) {
        if (str == null) {
            return null;
        }
        final int length = str.length();
        final StringBuilder b = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            final char c = str.charAt(i);
            final char newChar;
            if (c < 128) {
                newChar = c;
            } else if (c == 'é' || c == 'è' || c == 'ê') {
                newChar = 'e';
            } else if (c == 'â' || c == 'à') {
                newChar = 'a';
            } else if (c == 'î') {
                newChar = 'i';
            } else if (c == 'ù' || c == 'û') {
                newChar = 'u';
            } else if (c == 'ô') {
                newChar = 'o';
            } else if (c == 'ç') {
                newChar = 'c';
            } else {
                newChar = ' ';
            }
            b.append(newChar);
        }
        return b.toString();
    }

    public static final Matcher findFirstContaining(final String s, final Pattern... patterns) {
        for (final Pattern p : patterns) {
            final Matcher matcher = p.matcher(s);
            if (matcher.find())
                return matcher;
        }
        return null;
    }

    static public interface Search {
        public boolean equals(String str, String anotherString);

        public boolean startsWith(String str, String prefix);

        public boolean endsWith(String str, String suffix);

        public boolean contains(String str, String anotherString);
    }

    static public final Search EXACT_SEARCH = new Search() {
        @Override
        public boolean startsWith(String str, String prefix) {
            return str.startsWith(prefix);
        }

        @Override
        public boolean equals(String str, String anotherString) {
            return str.equals(anotherString);
        }

        @Override
        public boolean endsWith(String str, String suffix) {
            return str.endsWith(suffix);
        }

        @Override
        public boolean contains(String str, String searchStr) {
            return str.contains(searchStr);
        }
    };

    // Simple (Single-Character) Case Mapping
    static public final Search SIMPLE_CASE_MAPPING_SEARCH = new Search() {
        @Override
        public boolean startsWith(String str, String prefix) {
            return str.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        @Override
        public boolean equals(String str, String anotherString) {
            return str.equalsIgnoreCase(anotherString);
        }

        @Override
        public boolean endsWith(String str, String suffix) {
            final int suffixLength = suffix.length();
            return str.regionMatches(true, str.length() - suffixLength, suffix, 0, suffixLength);
        }

        @Override
        public boolean contains(String str, String searchStr) {
            final int length = searchStr.length();
            if (length == 0)
                return true;

            for (int i = str.length() - length; i >= 0; i--) {
                if (str.regionMatches(true, i, searchStr, 0, length))
                    return true;
            }
            return false;
        }
    };

    static public abstract class NormalizeSearch implements Search {

        protected abstract String normalize(String s);

        @Override
        public boolean startsWith(String str, String prefix) {
            return normalize(str).startsWith(normalize(prefix));
        }

        @Override
        public boolean equals(String str, String anotherString) {
            return normalize(str).equals(normalize(anotherString));
        }

        @Override
        public boolean endsWith(String str, String suffix) {
            return normalize(str).endsWith(normalize(suffix));
        }

        @Override
        public boolean contains(String str, String searchStr) {
            return normalize(str).contains(normalize(searchStr));
        }
    };

    // Fixed Locale
    static public final class LowerCaseMappingSearch extends NormalizeSearch {

        private final Locale locale;

        public LowerCaseMappingSearch(Locale locale) {
            super();
            this.locale = locale;
        }

        public final Locale getLocale() {
            return this.locale;
        }

        @Override
        protected String normalize(String s) {
            return s.toLowerCase(this.getLocale());
        }
    };

    // Locale.getDefault() at the time of the call
    static public final Search DEFAULT_LOWERCASE_MAPPING_SEARCH = new NormalizeSearch() {
        @Override
        protected String normalize(String s) {
            return s.toLowerCase();
        }
    };

    static public final Search CASE_FOLDING_SEARCH = new NormalizeSearch() {
        @Override
        protected String normalize(String s) {
            return normalizeCase(s);
        }
    };

    static private final Pattern BLANK_PATTERN = Pattern.compile("\\p{Blank}+");

    // replace tabs and multiple spaces by one space
    static public final String normalizeBlanks(String s) {
        return BLANK_PATTERN.matcher(s.trim()).replaceAll(" ");
    }

    static private final Pattern DIACRITICAL_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    static public String removeDiacritical(final String s) {
        return DIACRITICAL_PATTERN.matcher(Normalizer.normalize(s, Form.NFD)).replaceAll("");
    }

    static public final String normalizeCase(final String s) {
        // the JRE only does Single-Character Case Mapping so it can't handle Tschüß/TSCHÜSS
        // http://userguide.icu-project.org/transforms/casemappings
        // https://unicode.org/faq/casemap_charprop.html#casemap
        return UCharacter.foldCase(s, true);
    }

    static public final String normalizeForSearch(final String s) {
        return normalizeCase(removeDiacritical(normalizeBlanks(s)));
    }

    static public final String escapeHTML(String html) {
        final int length = html.length();
        final StringBuilder b = new StringBuilder(length * 2);

        for (int i = 0; i < length; i++) {
            final char c = html.charAt(i);
            if (c == '&') {
                b.append("&amp;");
            } else if (c == '\"') {
                b.append("&quot;");
            } else if (c == '<') {
                b.append("&lt;");
            } else if (c == '>') {
                b.append("&gt;");
            }
            b.append(c);
        }

        return b.toString();
    }

}