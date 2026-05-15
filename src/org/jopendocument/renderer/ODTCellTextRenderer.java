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

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

import org.jopendocument.model.style.StyleStyle;
import org.jopendocument.model.style.StyleTableCellProperties;
import org.jopendocument.model.table.TableTableCell;
import org.jopendocument.model.text.TextP;
import org.jopendocument.util.ValueHelper;

public class ODTCellTextRenderer implements ODTCellRenderer {

    public void draw(Graphics2D g, int x, int y, int cellWidth, int cellHeight, double resizeFactor, TableTableCell cell, StyleTableCellProperties cellProps) {
        final List<TextP> textp = cell.getTextP();
        if (textp != null) {
            if (textp.isEmpty()) {
                return;
            }
            final StyleStyle cellStyle = cell.getStyle();
            if (cellStyle == null) {
                g.setColor(Color.RED);
                g.drawRect(x, y, cellWidth, cellHeight);
                return;
            }
            final ODTCellText text = new ODTCellText(g, cell, textp, resizeFactor, cellStyle, cellProps, cellWidth);
            if (!text.isEmpty()) {
                if (cellProps == null) {
                    cellProps = new StyleTableCellProperties();
                }
                if (cellProps.getVerticalAlign() == null) {
                    cellProps.setVerticalAlign("bottom");
                }

                int offsetY = 0;
                final String verticalAlign = cellProps.getVerticalAlign();
                if (verticalAlign.equals("middle")) {
                    offsetY = (cellHeight + text.getHeight()) / 2;
                } else if (verticalAlign.equals("top")) {
                    offsetY = text.getHeight();
                    offsetY += getPaddingTop(cellProps, resizeFactor);
                } else {
                    // By default, bottom alignment
                    offsetY = cellHeight;
                    offsetY -= getPaddingBottom(cellProps, resizeFactor);
                }

                text.draw(cell, cellWidth, getPaddingLeft(cellProps, resizeFactor), getPaddingRight(cellProps, resizeFactor), x, y + offsetY, cellProps.mustWrapText());

            }
        }

    }

    public static int getPaddingLeft(StyleTableCellProperties props, double resizeFactor) {
        String padding = props.getPaddingLeft();
        if (padding == null) {
            padding = StyleTableCellProperties.DEFAULT_PADDING;
        }
        return 1 + (int) Math.round((ValueHelper.getLength(padding) / resizeFactor));
    }

    public static int getPaddingRight(StyleTableCellProperties props, double resizeFactor) {
        String padding = props.getPaddingRight();
        if (padding == null) {
            padding = StyleTableCellProperties.DEFAULT_PADDING;
        }
        return 1 + (int) Math.round((ValueHelper.getLength(padding) / resizeFactor));
    }

    public static int getPaddingTop(StyleTableCellProperties props, double resizeFactor) {
        String padding = props.getPaddingTop();
        if (padding == null) {
            padding = StyleTableCellProperties.DEFAULT_PADDING;
        }
        return 1 + (int) Math.round((ValueHelper.getLength(padding) / resizeFactor));
    }

    public static int getPaddingBottom(StyleTableCellProperties props, double resizeFactor) {
        String padding = props.getPaddingBottom();
        if (padding == null) {
            padding = StyleTableCellProperties.DEFAULT_PADDING;
        }
        return 1 + (int) Math.round((ValueHelper.getLength(padding) / resizeFactor));
    }
}
