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

import java.awt.Font;
import java.awt.Graphics2D;

import org.jopendocument.model.style.StyleStyle;
import org.jopendocument.model.style.StyleTextProperties;

public class ODTCellTextItem {

    private Graphics2D g2;
    // Text (including space)
    private String text;

    private double resizeFactor;

    private StyleStyle textStyle;

    public ODTCellTextItem(Graphics2D g2, String textSpan, double resizeFactor, StyleStyle textStyle) {
        this.g2 = g2;
        if (textSpan == null) {
            throw new IllegalArgumentException("null text");
        }
        this.text = textSpan;
        this.resizeFactor = resizeFactor;
        this.textStyle = textStyle;
    }

    public ODTCellTextItem(ODTCellTextItem item, String textSpan) {
        this.g2 = item.g2;
        if (textSpan == null) {
            throw new IllegalArgumentException("null text");
        }
        this.text = textSpan;
        this.resizeFactor = item.resizeFactor;
        this.textStyle = item.textStyle;
    }

    public int getHeight() {
        try {
            this.g2.setFont(this.getFont());
            return Math.round(this.g2.getFontMetrics().getAscent() - this.g2.getFontMetrics().getDescent() + this.g2.getFontMetrics().getHeight() / 2.7f);
        } catch (Exception e) {
            e.printStackTrace();
            return 10;
        }
    }

    public int getWidthWithoutSpace() {
        this.g2.setFont(this.getFont());
        return this.g2.getFontMetrics().stringWidth(getText().trim());
    }

    public int getWidth() {
        this.g2.setFont(this.getFont());
        return this.g2.getFontMetrics().stringWidth(getText());
    }

    public Font getFont() {
        if (this.textStyle != null) {
            final StyleTextProperties styleTextProperties = this.textStyle.getStyleTextProperties();
            if (styleTextProperties != null) {
                return styleTextProperties.getFont(this.resizeFactor);
            }
        } else {
            System.err.println("ODTCellTextItem:textStyle null: '" + getText() + "'");
        }
        return null;
    }

    public String getColor() {
        if (this.textStyle != null) {
            final StyleTextProperties styleTextProperties = this.textStyle.getStyleTextProperties();
            if (styleTextProperties != null) {
                String color = styleTextProperties.getColor();
                if (color == null) {
                    color = "#000000";
                }
                return color;
            }
        } else {
            System.err.println("ODTCellTextItem:textStyle null: '" + getText() + "'");
        }
        return "#000000";
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isUnderlined() {
        if (this.textStyle != null) {
            final StyleTextProperties styleTextProperties = this.textStyle.getStyleTextProperties();
            if (styleTextProperties != null) {
                String s = styleTextProperties.getTextUnderlineStyle();
                return (s != null && s.equals("solid"));
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "TextItem:'" + this.getText() + "'";
    }

}
