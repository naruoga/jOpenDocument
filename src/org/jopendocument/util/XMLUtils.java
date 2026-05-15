package org.jopendocument.util;

import org.jopendocument.util.Tuple2.List2;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class XMLUtils {

    public static final String escapeAttribute(final String value) {
        final int len = value.length();
        int idx = 0;

        checkloop: while (idx < len) {
            final char ch = value.charAt(idx);
            if (ch == '<' || ch == '>' || ch == '&' || ch == '\r' || ch == '\n' || ch == '"' || ch == '\t') {
                break checkloop;
            }
            idx++;
        }

        if (idx == len) {
            return value;
        }

        final StringBuilder sb = new StringBuilder(len + 5);
        sb.append(value, 0, idx);
        while (idx < len) {
            final char ch = value.charAt(idx++);
            switch (ch) {
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            case '\r':
                sb.append("&#xD;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\t':
                sb.append("&#x9;");
                break;
            case '\n':
                sb.append("&#xA;");
                break;
            default:
                sb.append(ch);
                break;
            }
        }

        return sb.toString();
    }

    public static final String escapeText(final String value) {
        final int right = value.length();
        int idx = 0;
        checkloop: while (idx < right) {
            final char ch = value.charAt(idx);
            if (ch == '<' || ch == '>' || ch == '&' || ch == '\r' || ch == '\n') {
                break checkloop;
            }
            idx++;
        }

        if (idx == right) {
            // no escape needed.
            return value;
        }

        final StringBuilder sb = new StringBuilder();
        if (idx > 0) {
            sb.append(value, 0, idx);
        }

        while (idx < right) {
            final char ch = value.charAt(idx++);
            switch (ch) {
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            case '\r':
                sb.append("&#xD;");
                break;
            case '\n':
                sb.append('\n');
                break;
            default:
                sb.append(ch);
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Split prefix and local name.
     * 
     * @param qName a qualified name, e.g. "ns:foo" or "foo".
     * @return first the prefix (<code>null</code> if none), then the local name
     * @throws IllegalArgumentException if invalid syntax.
     * @see <a href="https://www.w3.org/TR/xml-names/#ns-qualnames">Qualified Names</a>
     */
    public static List2<String> splitQualifiedName(final String qName) throws IllegalArgumentException {
        final int indexOfColon = qName.indexOf(':');
        if (indexOfColon < 0)
            return new List2<>(null, qName);

        if (indexOfColon == 0)
            throw new IllegalArgumentException("Starts with colon : '" + qName + "'");
        final int l = qName.length();
        assert indexOfColon < l;
        if (indexOfColon == l - 1)
            throw new IllegalArgumentException("Ends with colon : '" + qName + "'");
        final int secondColon = qName.indexOf(':', indexOfColon + 1);
        if (secondColon >= 0)
            throw new IllegalArgumentException("Two colons : '" + qName + "'");
        return new List2<>(qName.substring(0, indexOfColon), qName.substring(indexOfColon + 1));
    }

    public static final void skipToEndElement(final XMLStreamReader reader) throws XMLStreamException {
        if (!reader.isStartElement())
            throw new IllegalStateException("Not at a start of an element : " + reader.getEventType() + ", " + reader.getLocation());
        final String name = reader.getLocalName();
        int depth = 1;
        while (!(depth == 0 && reader.isEndElement() && reader.getLocalName().equals(name))) {
            final int eventType = reader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT)
                depth++;
            else if (eventType == XMLStreamConstants.END_ELEMENT)
                depth--;
        }
    }
}
