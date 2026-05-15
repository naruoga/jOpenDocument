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
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import org.jopendocument.util.ValueHelper;

public class ODTCellTextLine {

    private List<ODTCellTextItem> items = new ArrayList<>(2);
    private int maxH = 0;
    int w = -1;
    private boolean last = false;

    private static final boolean DEBUG = false;

    public int getHeight() {
        return this.maxH;
    }

    public int getWidth() {
        if (this.w < 0) {
            computeWidth();
        }
        return this.w;
    }

    public List<ODTCellTextItem> getItems() {
        return this.items;
    }

    public String getFullText() {
        StringBuilder b = new StringBuilder();
        for (ODTCellTextItem item : this.items) {
            b.append(item.getText());
        }
        return b.toString();
    }

    public void draw(Graphics2D g2, int startX, int y) {
        int x = startX;
        final int size = this.items.size();
        for (int i = 0; i < size; i++) {
            final ODTCellTextItem item = this.items.get(i);
            final Font f = item.getFont();
            g2.setFont(f);
            g2.setColor(ValueHelper.getColor(item.getColor()));
            final String text = item.getText();
            if (text != null && !text.isEmpty()) {
                g2.drawString(text, x, y);
                if (DEBUG) {
                    System.out.println("ODTCellTextLine.draw() at (" + x + "," + y + ") " + f.getFontName() + " : " + f.getName() + " :" + f.getFamily() + " : '" + text + "'");
                    g2.setColor(Color.RED);
                    g2.drawLine(x, y + 3, x + item.getWidth(), y + 3);
                }
                if (item.isUnderlined()) {
                    int yUnder = y + getHeight() / 10 + 1;
                    g2.drawLine(x, yUnder, x + item.getWidth(), yUnder);
                }
                x += item.getWidth();
            }
        }
    }

    public void drawJustify(Graphics2D g2, int x, int y, int paddingLeft, int paddingRight, int cellWidth) {
        final int maxLineWidth = cellWidth - paddingLeft - paddingRight;

        int cx = x + paddingLeft;
        int cy = y;

        // extraSpace is the empty space that should be equally distributed between words
        int extraSpace = maxLineWidth - this.getWidth();

        cy += this.getHeight();
        final List<ODTCellTextItem> itemsInLine = getItems();
        if (!itemsInLine.isEmpty()) {
            int[] spaces = new int[itemsInLine.size() - 1];

            int index = 0;
            for (int j = 0; j < extraSpace; j++) {
                if (itemsInLine.get(index).getText().endsWith(" ")) {
                    spaces[index]++;
                }
                index++;
                if (index > spaces.length - 1) {
                    index = 0;
                }
            }

            final int itemCount = itemsInLine.size() - 1;
            for (int j = 0; j < itemCount; j++) {
                ODTCellTextItem item = itemsInLine.get(j);
                Font f = item.getFont();
                g2.setFont(f);
                g2.setColor(ValueHelper.getColor(item.getColor()));
                String text = item.getText();
                if (DEBUG) {
                    System.out.println("ODTCellTextLine.drawJustify() at (" + x + "," + y + ") " + f.getFontName() + " : " + text);
                }
                g2.drawString(text, cx, cy);
                cx += item.getWidth();
                if (text.endsWith(" ")) {
                    cx += spaces[j];
                }
            }
            // Last item is aligned at right
            ODTCellTextItem item = itemsInLine.get(itemCount);
            Font f = item.getFont();
            g2.setFont(f);
            g2.setColor(ValueHelper.getColor(item.getColor()));
            String text = item.getText();
            if (!isLast()) {
                cx = x + cellWidth - paddingRight - item.getWidthWithoutSpace();
            }
            if (!text.isEmpty()) {
                g2.drawString(text, cx, cy);
            }
        }

    }

    public List<ODTCellTextItem> splitAtSpaces() {
        if (this.items.size() == 1 && this.items.get(0).getText().trim().isEmpty()) {
            return this.items;
        }
        final List<ODTCellTextItem> result = new ArrayList<>();
        for (ODTCellTextItem item : this.items) {
            final String text = item.getText();
            int s = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ' && i > 0) {
                    final String subString = text.substring(s, i + 1);
                    final ODTCellTextItem it = new ODTCellTextItem(item, subString);
                    result.add(it);
                    s = i + 1;
                }
            }
            if (s < text.length()) {
                final String subString = text.substring(s, text.length());
                final ODTCellTextItem it = new ODTCellTextItem(item, subString);
                result.add(it);
            }
        }
        return result;
    }

    public void add(ODTCellTextItem item) {
        this.items.add(item);
        int height = item.getHeight();
        if (height > this.maxH) {
            this.maxH = height;
        }

    }

    public void setLast(boolean b) {
        this.last = b;
    }

    /**
     * @return true if last line of a paragraph
     */
    boolean isLast() {
        return this.last;
    }

    public void trimLastItem() {
        if (!this.items.isEmpty()) {
            final ODTCellTextItem item = this.items.get(this.items.size() - 1);
            item.setText(item.getText().trim());
            computeWidth();
        }

    }

    private void computeWidth() {
        this.w = 0;
        for (ODTCellTextItem item : this.items) {
            this.w += item.getWidth();
        }
    }

}
