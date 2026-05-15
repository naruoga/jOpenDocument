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

package org.jopendocument.model.draw;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class DrawFrame {

    private DrawImage drawImage;

    protected String svgHeight;

    protected String svgWidth;

    protected String svgX;

    protected String svgY;

    private List<DrawTextBox> textBoxes = new ArrayList<DrawTextBox>(3);

    private int zIndex;

    private boolean tableBackground;

    public DrawImage getDrawImage() {
        return this.drawImage;
    }

    /**
     * Gets the value of the svgHeight property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getSvgHeight() {
        return this.svgHeight;
    }

    /**
     * Gets the value of the svgWidth property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getSvgWidth() {
        return this.svgWidth;
    }

    /**
     * Gets the value of the svgX property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getSvgX() {
        return this.svgX;
    }

    /**
     * Gets the value of the svgY property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getSvgY() {
        return this.svgY;
    }

    public void setDrawImage(final DrawImage p) {
        this.drawImage = p;

    }

    /**
     * Sets the value of the svgHeight property.
     * 
     * @param value allowed object is {@link String }
     * 
     */
    public void setSvgHeight(final String value) {
        this.svgHeight = value;
    }

    /**
     * Sets the value of the svgWidth property.
     * 
     * @param value allowed object is {@link String }
     * 
     */
    public void setSvgWidth(final String value) {
        this.svgWidth = value;
    }

    /**
     * Sets the value of the svgX property.
     * 
     * @param value allowed object is {@link String }
     * 
     */
    public void setSvgX(final String value) {
        this.svgX = value;
    }

    /**
     * Sets the value of the svgY property.
     * 
     * @param value allowed object is {@link String }
     * 
     */
    public void setSvgY(final String value) {
        this.svgY = value;
    }

    public void addTextBox(DrawTextBox textBox) {
        this.textBoxes.add(textBox);

    }

    public void setZIndex(String value) {
        if (value == null) {
            value = "0";
        }
        try {
            this.zIndex = Integer.parseInt(value);
        } catch (Exception e) {
            this.zIndex = 0;
        }
    }

    public int getzIndex() {
        return zIndex;
    }

    public void setTableBackground(String value) {
        if (value == null) {
            value = "false";
        }
        if (value.equals("true")) {
            tableBackground = true;
        }
    }

    public boolean isTableBackground() {
        return tableBackground;
    }
}
