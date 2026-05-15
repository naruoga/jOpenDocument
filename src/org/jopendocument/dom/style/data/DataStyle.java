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

package org.jopendocument.dom.style.data;

import org.jopendocument.dom.Feature;
import org.jopendocument.dom.Log;
import org.jopendocument.dom.ODEpoch;
import org.jopendocument.dom.ODPackage;
import org.jopendocument.dom.ODValueType;
import org.jopendocument.dom.OOUtils;
import org.jopendocument.dom.Style;
import org.jopendocument.dom.StyleDesc;
import org.jopendocument.dom.StyleProperties;
import org.jopendocument.dom.XMLVersion;
import org.jopendocument.dom.spreadsheet.CellStyle;
import org.jopendocument.dom.text.TextStyle.StyleTextProperties;
import org.jopendocument.util.NumberUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;

// from section 16.27 in v1.2-cs01-part1
public abstract class DataStyle extends Style {
    private static final int DEFAULT_GROUPING_SIZE = new DecimalFormat().getGroupingSize();
    // 15 as of LibreOffice 6, was 10 earlier
    /**
     * The default number of decimal digits if neither defined in the style nor in default-style.
     */
    public static final int DEFAULT_DECIMAL_PLACES;
    private static final Pattern QUOTE_PATRN = Pattern.compile("'", Pattern.LITERAL);
    private static final Pattern EXP_PATTERN = Pattern.compile("E(\\d+)$");

    public static int getDecimalPlaces(final CellStyle defaultStyle) {
        if (defaultStyle != null) {
            final int res = defaultStyle.getTableCellProperties(null).getDecimalPlaces();
            // Ignore invalid value
            return res < 0 ? DEFAULT_DECIMAL_PLACES : res;
        } else {
            return DEFAULT_DECIMAL_PLACES;
        }
    }

    protected static final int parsePositive(final String attr, final boolean lenient) {
        final int res = Integer.parseInt(attr);
        if (res < 0) {
            reportError("Negative value for " + attr, lenient);
            return 0;
        }
        return res;
    }

    public static void addStringLiteral(final StringBuilder formatSB, final String s) {
        formatSB.append('\'');
        formatSB.append(QUOTE_PATRN.matcher(s).replaceAll("''"));
        formatSB.append('\'');
    }

    public static final Set<Class<? extends DataStyle>> DATA_STYLES;
    private static final DataStyleDesc<?>[] DATA_STYLES_DESCS = new DataStyleDesc<?>[] { NumberStyle.DESC, PercentStyle.DESC, TextStyle.DESC, CurrencyStyle.DESC, DateStyle.DESC, TimeStyle.DESC,
            BooleanStyle.DESC };
    static {
        final Set<Class<? extends DataStyle>> l = new HashSet<Class<? extends DataStyle>>(DATA_STYLES_DESCS.length);
        l.add(NumberStyle.class);
        l.add(PercentStyle.class);
        l.add(TextStyle.class);
        l.add(CurrencyStyle.class);
        l.add(DateStyle.class);
        l.add(TimeStyle.class);
        l.add(BooleanStyle.class);
        DATA_STYLES = Collections.unmodifiableSet(l);
        assert DATA_STYLES_DESCS.length == DATA_STYLES.size() : "Discrepancy between classes and descs";

        final String decPlacesProp = System.getProperty("openDocument.defaultDecimalPlaces");
        final int decPlacesParsed = decPlacesProp == null ? -1 : Integer.parseInt(decPlacesProp);
        // Ignore invalid value
        DEFAULT_DECIMAL_PLACES = decPlacesParsed < 0 ? 15 : decPlacesParsed;
        assert DEFAULT_DECIMAL_PLACES >= 0;
    }

    public static abstract class DataStyleDesc<S extends DataStyle> extends StyleDesc<S> {

        protected DataStyleDesc(Class<S> clazz, XMLVersion version, String elemName, String baseName) {
            super(clazz, version, elemName, baseName);
            this.setElementNS(getVersion().getNS("number"));
            // from 19.469 in v1.2-cs01-part1
            this.getRefElementsMap().addAll("style:data-style-name",
                    Arrays.asList("presentation:date-time-decl", "style:style", "text:creation-date", "text:creation-time", "text:database-display", "text:date", "text:editing-duration",
                            "text:expression", "text:meta-field", "text:modification-date", "text:modification-time", "text:print-date", "text:print-time", "text:table-formula", "text:time",
                            "text:user-defined", "text:user-field-get", "text:user-field-input", "text:variable-get", "text:variable-input", "text:variable-set"));
            this.getRefElementsMap().add("style:apply-style-name", "style:map");
        }
    }

    static public void registerDesc() {
        for (final StyleDesc<?> d : DATA_STYLES_DESCS)
            Style.registerAllVersions(d);
    }

    static public <S extends DataStyle> DataStyleDesc<S> getDesc(final Class<S> clazz, final XMLVersion version) {
        return (DataStyleDesc<S>) Style.getStyleDesc(clazz, version);
    }

    private final ODValueType type;
    private StyleTextProperties textProps;

    protected DataStyle(final ODPackage pkg, Element elem, final ODValueType type) {
        super(pkg, elem);
        this.type = type;
    }

    public final ODValueType getDataType() {
        return this.type;
    }

    public final ODEpoch getEpoch() {
        return this.getPackage().getODDocument().getEpoch();
    }

    /**
     * Convert the passed object to something that {@link #format(Object, CellStyle, boolean)} can
     * accept.
     * 
     * @param o the object to convert.
     * @return an object that can be formatted, <code>null</code> if <code>o</code> cannot be
     *         converted.
     * @throws NullPointerException if <code>o</code> is <code>null</code>.
     * @see #canFormat(Class)
     */
    public final Object convert(final Object o) throws NullPointerException {
        if (o == null)
            throw new NullPointerException();

        final Object res;
        if (this.canFormat(o.getClass()))
            res = o;
        else
            res = this.convertNonNull(o);
        assert res == null || this.canFormat(res.getClass());
        return res;
    }

    // o is not null and canFormat(o.getClass()) is false
    // return null if o cannot be converted
    protected abstract Object convertNonNull(Object o);

    /**
     * Whether instances of the passed class can be {@link #format(Object, CellStyle, boolean)
     * formatted}.
     * 
     * @param toFormat the class.
     * @return <code>true</code> if instances of <code>toFormat</code> can be formatted.
     */
    public final boolean canFormat(Class<?> toFormat) {
        return this.getDataType().canFormat(toFormat);
    }

    public final String getTitle() {
        return this.getElement().getAttributeValue("title", getElement().getNamespace());
    }

    public final StyleTextProperties getTextProperties() {
        if (this.textProps == null)
            this.textProps = new StyleTextProperties(this);
        return this.textProps;
    }

    public abstract String format(final Object o, final CellStyle defaultStyle, boolean lenient) throws UnsupportedOperationException;

    static protected final void reportError(String msg, boolean lenient) throws UnsupportedOperationException {
        if (lenient)
            Log.get().warning(msg);
        else
            throw new UnsupportedOperationException(msg);
    }

    public final Locale getLocale() {
        return this.getLocale(false);
    }

    public final Locale getLocale(final boolean local) {
        return this.getLocale(this.getElement(), local);
    }

    protected final Locale getLocale(final Element elem, final boolean local) {
        final Locale res = OOUtils.getElementLocale(elem);
        return local || res != null ? res : this.getPackage().getLocale();
    }

    public final void setLocale(final Locale l) {
        OOUtils.setElementLocale(this.getElement(), this.getElement().getNamespace(), l);
    }

    @SuppressWarnings("unchecked")
    public final List<Element> getMapChildren() {
        return this.getElement().getChildren("map", getSTYLE());
    }

    protected final String formatNumberOrScientificNumber(final Element elem, final Number n, CellStyle defaultStyle, final boolean lenient) {
        return this.formatNumberOrScientificNumber(elem, n, 1, defaultStyle, lenient);
    }

    protected final String formatNumberOrScientificNumber(final Element elem, final Number n, final int multiplier, CellStyle defaultStyle, final boolean lenient) {
        final Namespace numberNS = this.getElement().getNamespace();
        final StringBuilder numberSB = new StringBuilder();

        final List<?> embeddedTexts = elem.getChildren("embedded-text", numberNS);
        final SortedMap<Integer, String> embeddedTextByPosition = new TreeMap<Integer, String>(Collections.reverseOrder());
        for (final Object o : embeddedTexts) {
            final Element embeddedText = (Element) o;
            embeddedTextByPosition.put(Integer.valueOf(embeddedText.getAttributeValue("position", numberNS)), embeddedText.getText());
        }

        final Attribute factorAttr = elem.getAttribute("display-factor", numberNS);
        final double factor = (factorAttr != null ? Double.valueOf(factorAttr.getValue()) : 1) / multiplier;

        // default value from 19.348
        final boolean grouping = StyleProperties.parseBoolean(elem.getAttributeValue("grouping", numberNS), false);

        final String minIntDigitsAttr = elem.getAttributeValue("min-integer-digits", numberNS);
        final int minIntDig = minIntDigitsAttr == null ? 0 : Integer.parseInt(minIntDigitsAttr);
        if (minIntDig == 0) {
            numberSB.append('#');
        } else {
            for (int i = 0; i < minIntDig; i++)
                numberSB.append('0');
        }

        // e.g. if it's "--", 12,3 is displayed "12,3" and 12 is displayed "12,--"
        // From v1.3 §19.356.2 decimal-replacement can be empty
        final String decReplacement = elem.getAttributeValue("decimal-replacement", numberNS, "");
        final boolean decSeparatorAlwaysShown;
        if (!decReplacement.isEmpty() && !NumberUtils.hasFractionalPart(n)) {
            decSeparatorAlwaysShown = true;
            numberSB.append('.');
            // escape quote in replacement
            addStringLiteral(numberSB, decReplacement);
        } else {
            decSeparatorAlwaysShown = false;
            // see 19.343.2
            final String decPlacesAttr = elem.getAttributeValue("decimal-places", numberNS, "");
            final int forcedPlaces, nonZeroPlaces;
            if (!decPlacesAttr.isEmpty()) {
                final int decPlaces = parsePositive(decPlacesAttr, lenient);

                final String officeVers = this.getPackage().getFormatVersion().getOfficeVersion();
                final Feature minDecFeature;
                if (officeVers.equals("1.2")) {
                    minDecFeature = Feature.EXTENSION;
                } else if (officeVers.equals("1.0") || officeVers.equals("1.1")) {
                    minDecFeature = Feature.UNSUPPORTED;
                } else {
                    minDecFeature = Feature.SUPPORTED;
                }

                if (minDecFeature == Feature.UNSUPPORTED) {
                    forcedPlaces = decPlaces;
                    nonZeroPlaces = 0;
                } else {
                    final Namespace minDecNS = minDecFeature == Feature.EXTENSION ? this.getNS().getNS("loext") : numberNS;
                    final String minDecPlacesAttr = elem.getAttributeValue("min-decimal-places", minDecNS, "");
                    if (minDecPlacesAttr.isEmpty()) {
                        // - minDecFeature == Feature.SUPPORTED is from observation (and if a 1.2
                        // document w/o "min-decimal-places" is saved by LO, then the attribute is
                        // explicitly added with a value equal to "decimal-places")
                        // - for the rest of the condition, see 19.356.2 & 3 of v1.3
                        if (minDecFeature == Feature.SUPPORTED && decReplacement.isEmpty() && elem.getName().equals("number")) {
                            forcedPlaces = 0;
                            nonZeroPlaces = decPlaces;
                        } else {
                            forcedPlaces = decPlaces;
                            nonZeroPlaces = 0;
                        }
                    } else {
                        forcedPlaces = parsePositive(minDecPlacesAttr, lenient);
                        if (forcedPlaces > decPlaces) {
                            DataStyle.reportError("min-decimal-places greater than decimal-places : " + minDecPlacesAttr + " > " + decPlacesAttr, lenient);
                            nonZeroPlaces = 0;
                        } else {
                            nonZeroPlaces = decPlaces - forcedPlaces;
                        }
                    }
                }
            } else {
                // default style specifies the maximum
                forcedPlaces = 0;
                nonZeroPlaces = getDecimalPlaces(defaultStyle);
            }

            if (forcedPlaces + nonZeroPlaces > 0) {
                numberSB.append('.');
                for (int i = 0; i < forcedPlaces; i++)
                    numberSB.append('0');
                for (int i = 0; i < nonZeroPlaces; i++)
                    numberSB.append('#');
            }
        }

        final Attribute minExpAttr = elem.getAttribute("min-exponent-digits", numberNS);
        final boolean forcedExpSign;
        if (minExpAttr != null) {
            forcedExpSign = Boolean.parseBoolean(elem.getAttributeValue("forced-exponent-sign", numberNS, "true"));
            numberSB.append('E');
            for (int i = 0; i < Integer.parseInt(minExpAttr.getValue()); i++)
                numberSB.append('0');
        } else {
            forcedExpSign = false;
        }

        final DecimalFormatSymbols symbols = new DecimalFormatSymbols(this.getLocale());

        final DecimalFormat decFormat = new DecimalFormat(numberSB.toString(), symbols);
        // Java always use HALF_EVEN
        decFormat.setRoundingMode(RoundingMode.HALF_UP);
        decFormat.setGroupingUsed(grouping);
        // needed since the default size is overwritten by the pattern
        decFormat.setGroupingSize(DEFAULT_GROUPING_SIZE);
        decFormat.setDecimalSeparatorAlwaysShown(decSeparatorAlwaysShown);
        String res = decFormat.format(NumberUtils.divide(n, factor));
        // There's no way to force the plus sign in DecimalFormat
        if (forcedExpSign) {
            final Matcher m = EXP_PATTERN.matcher(res);
            if (m.find())
                res = res.substring(0, m.start()) + "E+" + m.group(1);
        }
        if (embeddedTextByPosition.size() > 0) {
            final int intDigits = Math.max(minIntDig, NumberUtils.intDigits(n));
            // each time we insert text the decimal point moves
            int offset = 0;
            // sorted descending to avoid overwriting
            for (Entry<Integer, String> e : embeddedTextByPosition.entrySet()) {
                final String embeddedText = e.getValue();
                // the text will be before this index
                final int index = Math.max(0, offset + intDigits - e.getKey().intValue());
                res = res.substring(0, index) + embeddedText + res.substring(index);
                offset += embeddedText.length();
            }
        }
        return res;
    }
}
