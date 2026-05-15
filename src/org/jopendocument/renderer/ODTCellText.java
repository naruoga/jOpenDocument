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

package org.jopendocument.renderer;

import org.jopendocument.util.StringUtils;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import org.jopendocument.model.style.StyleParagraphProperties;
import org.jopendocument.model.style.StyleStyle;
import org.jopendocument.model.style.StyleTableCellProperties;
import org.jopendocument.model.style.StyleTextProperties;
import org.jopendocument.model.table.TableTableCell;
import org.jopendocument.model.text.TextP;
import org.jopendocument.model.text.TextSpan;

public class ODTCellText {

    private List<ODTCellTextLine> lines;

    private List<TextP> textP;

    private Graphics2D g2;

    private double resizeFactor;

    private StyleStyle cellTextStyle;

    public ODTCellText(Graphics2D g, TableTableCell cell, List<TextP> textp, double resizeFactor, StyleStyle cellStyle, StyleTableCellProperties cellProps, int cellWidth) {
        this.textP = textp;
        this.g2 = g;
        this.resizeFactor = resizeFactor;
        if (cellStyle == null) {
            throw new IllegalArgumentException("Default style null");
        }
        this.cellTextStyle = cellStyle;
        computeLines(cell, cellWidth, cellProps);
    }

    public String getFullText() {
        StringBuilder b = new StringBuilder();
        for (TextP text : this.textP) {
            List<TextSpan> lt = text.getTextSpans();
            for (TextSpan tp : lt) {
                if (tp.getValue() != null) {
                    b.append(tp.getValue());
                }
            }
        }
        return b.toString();
    }

    public boolean isEmpty() {
        for (TextP text : this.textP) {
            List<TextSpan> lt = text.getTextSpans();
            for (TextSpan tp : lt) {
                if (tp.getValue() != null && tp.getValue().length() > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public int getHeight() {
        int height = 1;
        for (ODTCellTextLine line : this.lines) {
            height += line.getHeight();
        }
        return height;
    }

    public int getWidth() {
        int width = 0;
        for (ODTCellTextLine line : this.lines) {
            width = Math.max(width, line.getWidth());
        }
        return width;

    }

    public void draw(TableTableCell cell, int cellWidth, int paddingLeft, int paddingRight, int x, int startY, boolean wrap) {
        int y = startY - this.getHeight();
        for (ODTCellTextLine line : this.lines) {

            boolean justify = false;
            int offsetX = 0;
            StyleParagraphProperties paragraphProps = cell.getStyle().getParagraphProperties();
            if (paragraphProps != null) {
                String textAlign = paragraphProps.getTextAlign();
                if (textAlign != null) {
                    if (textAlign.equals("center")) {
                        int strWidth = line.getWidth();
                        offsetX = (cellWidth - strWidth) / 2;
                    } else if (textAlign.equals("end")) {
                        int strWidth = line.getWidth();
                        offsetX = cellWidth - strWidth - paddingRight;
                    } else if (textAlign.equals("justify")) {
                        justify = true;
                        wrap = true;
                        offsetX += paddingLeft;
                    } else {
                        // Left
                        offsetX += paddingLeft;
                    }
                } else {
                    final String tableValueType = cell.getTableValueType();
                    if (tableValueType != null && tableValueType.equals("float")) {
                        int strWidth = line.getWidth();
                        offsetX = cellWidth - strWidth - paddingRight;
                    } else {
                        // Left
                        offsetX += paddingLeft;
                    }
                }
            } else {
                final String tableValueType = cell.getTableValueType();

                if (tableValueType != null && tableValueType.equals("float")) {
                    int strWidth = line.getWidth();
                    offsetX = cellWidth - strWidth - paddingRight;
                } else {
                    // Left
                    offsetX += paddingLeft;
                }
            }

            if (!justify || line.isLast()) {
                // Draw without justify
                line.draw(this.g2, x + offsetX, y + line.getHeight());
            } else {
                // Draw justify
                line.drawJustify(this.g2, x, y, paddingLeft, paddingRight, cellWidth);
            }
            y += line.getHeight();

        }

    }

    private void computeLines(TableTableCell cell, int cellWidth, StyleTableCellProperties cellProps) {
        final List<ODTCellTextLine> l = new ArrayList<>();
        for (TextP text : this.textP) {
            final List<TextSpan> lt = text.getTextSpans();
            ODTCellTextLine line = new ODTCellTextLine();
            if (lt.isEmpty()) {
                // Empty line
                ODTCellTextItem item = new ODTCellTextItem(this.g2, "", this.resizeFactor, this.cellTextStyle);
                line.add(item);
            } else {
                for (TextSpan textpan : lt) {
                    textpan.setTextStyle(mergeStyle(this.cellTextStyle, textpan.getTextStyle()));
                    String value = textpan.getValue();
                    if (value != null && !value.isEmpty()) {
                        if (value.contains("\n")) {
                            List<String> parts = StringUtils.fastSplit(value, '\n');
                            for (String p : parts) {
                                ODTCellTextItem item = new ODTCellTextItem(this.g2, p, this.resizeFactor, textpan.getTextStyle());
                                line.add(item);
                                l.add(line);
                                line = new ODTCellTextLine();
                            }
                        } else {
                            ODTCellTextItem item = new ODTCellTextItem(this.g2, value, this.resizeFactor, textpan.getTextStyle());
                            line.add(item);
                        }
                    }
                }
            }
            if (line.getItems().isEmpty()) {
                ODTCellTextItem item = new ODTCellTextItem(this.g2, "", this.resizeFactor, this.cellTextStyle);
                line.add(item);
            }
            l.add(line);

        }
        // Split lines using horintal alignment
        this.lines = new ArrayList<>();

        boolean wrap = false;
        if (cellProps != null) {
            wrap = cellProps.mustWrapText();
        }

        StyleParagraphProperties paragraphProps = cell.getStyle().getParagraphProperties();
        if (paragraphProps != null) {
            String textAlign = paragraphProps.getTextAlign();
            if (textAlign != null && textAlign.equals("justify")) {
                wrap = true;
            }
        }
        if (wrap) {
            final int maxLineWidth = cellWidth - ODTCellTextRenderer.getPaddingLeft(cellProps, resizeFactor) - ODTCellTextRenderer.getPaddingRight(cellProps, resizeFactor);
            for (ODTCellTextLine line : l) {
                this.lines.addAll(getWrappedLines(line, maxLineWidth));
            }
        } else {
            this.lines.addAll(l);
        }

    }

    public List<ODTCellTextLine> getWrappedLines(ODTCellTextLine line, int maxLineWidth) {
        List<ODTCellTextLine> result = new ArrayList<>();
        ODTCellTextLine currentLine = new ODTCellTextLine();
        result.add(currentLine);
        List<ODTCellTextItem> smallitems = line.splitAtSpaces();
        int w = 0;
        // Split the elements in lines
        for (ODTCellTextItem item : smallitems) {

            if (w + item.getWidthWithoutSpace() > maxLineWidth) {
                currentLine.trimLastItem();
                currentLine = new ODTCellTextLine();
                w = 0;
                result.add(currentLine);
            }
            w += item.getWidth();
            currentLine.add(item);
        }
        // Last can be empty, so we remove it
        if (result.get(result.size() - 1).getItems().isEmpty()) {
            result.remove(result.size() - 1);
        }
        result.get(result.size() - 1).setLast(true);
        return result;
    }

    private StyleStyle mergeStyle(StyleStyle cellTextStyle, StyleStyle spanTextStyle) {
        StyleStyle s = new StyleStyle();
        StyleTextProperties cellTextProperties = cellTextStyle.getStyleTextProperties();
        StyleTextProperties spanTextProperties = null;
        if (spanTextStyle != null) {
            spanTextProperties = spanTextStyle.getStyleTextProperties();
        }
        s.setTextProperties(mergeTextProperties(cellTextProperties, spanTextProperties));

        return s;
    }

    private StyleTextProperties mergeTextProperties(StyleTextProperties cellTextProperties, StyleTextProperties spanTextProperties) {

        // Merge font name
        String fontName = null;
        if (spanTextProperties != null) {
            fontName = spanTextProperties.getFontName();
        }
        if (fontName == null && cellTextProperties != null) {
            fontName = cellTextProperties.getFontName();
        }
        if (fontName == null) {
            fontName = "Arial";
        }
        // Merge font size
        String fontSize = null;
        if (spanTextProperties != null) {
            fontSize = spanTextProperties.getFontSize();
        }
        if (fontSize == null && cellTextProperties != null) {
            fontSize = cellTextProperties.getFontSize();
        }
        if (fontSize == null) {
            fontSize = "11pt";
        }
        // Merge font weight
        String fontWeight = null;
        if (spanTextProperties != null) {
            fontWeight = spanTextProperties.getFontWeight();
        }
        if (fontWeight == null && cellTextProperties != null) {
            fontWeight = cellTextProperties.getFontWeight();
        }
        if (fontWeight == null) {
            fontWeight = "normal";
        }
        // Merge font style
        String fontStyle = null;
        if (spanTextProperties != null) {
            fontStyle = spanTextProperties.getFontStyle();
        }
        if (fontStyle == null && cellTextProperties != null) {
            fontStyle = cellTextProperties.getFontStyle();
        }

        // Merge font color
        String fontColor = null;
        if (spanTextProperties != null) {
            fontColor = spanTextProperties.getColor();

        }
        if (fontColor == null && cellTextProperties != null) {
            fontColor = cellTextProperties.getColor();

        }
        if (fontColor == null) {
            fontColor = "#000000";
        }
        // Merge underlineStyle
        String underLineStyle = null;
        if (spanTextProperties != null) {
            underLineStyle = spanTextProperties.getTextUnderlineStyle();
        }
        if (underLineStyle == null && cellTextProperties != null) {
            underLineStyle = cellTextProperties.getTextUnderlineStyle();
        }
        // OK
        return StyleTextProperties.getStyleTextProperties(fontName, fontSize, fontWeight, fontStyle, fontColor, underLineStyle);
    }

}
