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

package org.jopendocument.dom;

import org.jopendocument.dom.spreadsheet.BytesProducer;
import org.jopendocument.dom.text.TextNode;

import java.math.BigDecimal;

import org.jdom.Element;
import org.jdom.Namespace;

/**
 * Represents a draw:frame, see 9.3 Frames.
 * 
 * @author Sylvain
 * @param <D> type of table parent
 */
public class ODFrame<D extends ODDocument> extends ImmutableDocStyledNode<GraphicStyle, D> {

    static public Element createEmpty(XMLVersion ns, final Number w, final Number h, final LengthUnit unit) {
        return createEmpty(ns, BigDecimal.ZERO, BigDecimal.ZERO, w, h, unit);
    }

    static public Element createEmpty(XMLVersion ns, final Number x, final Number y, final Number w, final Number h, final LengthUnit unit) {
        final Namespace svgNS = ns.getNS("svg");
        final Element res = new Element("frame", ns.getNS("draw"));
        res.setAttribute("x", unit.format(x), svgNS);
        res.setAttribute("y", unit.format(y), svgNS);
        res.setAttribute("width", unit.format(w), svgNS);
        res.setAttribute("height", unit.format(h), svgNS);
        return res;
    }

    /**
     * Parse SVG and OD length.
     * 
     * @param l the string to parse, eg "1.53cm".
     * @param to the unit, eg {@link LengthUnit#MM}.
     * @return the length, eg 15.3.
     */
    public static final float parseLength(final String l, final LengthUnit to) {
        return LengthUnit.parseLength(l, to).floatValue();
    }

    // BigDecimal are exact and they can be null (eg optional attribute)
    private final BigDecimal width, height;

    public ODFrame(D parent, Element frame) {
        super(parent, frame, GraphicStyle.class);
        this.width = LengthUnit.parseLength(this.getSVGAttr("width"), getUnit());
        this.height = LengthUnit.parseLength(this.getSVGAttr("height"), getUnit());
    }

    private final Element getTextBox() {
        final Element res;
        if (this.getODDocument().getVersion() == XMLVersion.OOo)
            res = this.getElement();
        else
            res = this.getElement().getChild("text-box", this.getElement().getNamespace("draw"));
        assert res.getName().equals("text-box");
        return res;
    }

    public final String getCharacterContent(final boolean ooMode) {
        return TextNode.getChildrenCharacterContent(this.getTextBox(), getODDocument().getFormatVersion(), ooMode);
    }

    public final BigDecimal getWidth() {
        return this.getWidth(this.getUnit());
    }

    public final BigDecimal getWidth(final LengthUnit in) {
        return this.getUnit().convertTo(this.width, in);
    }

    public final BigDecimal getHeight() {
        return this.getHeight(this.getUnit());
    }

    public final BigDecimal getHeight(final LengthUnit in) {
        return this.getUnit().convertTo(this.height, in);
    }

    private Namespace getSVG() {
        return getElement().getNamespace("svg");
    }

    public String getSVGAttr(String name) {
        return this.getElement().getAttributeValue(name, getSVG());
    }

    public void setSVGAttr(String name, String val) {
        this.getElement().setAttribute(name, val, this.getSVG());
    }

    /**
     * This set the svg:name attribute to val mm.
     * 
     * @param name the name of the attribute, eg "x".
     * @param val the value of the attribute in {@link #getUnit()}, eg 15.3.
     */
    public void setSVGAttr(String name, Number val) {
        this.setSVGAttr(name, this.getUnit().format(val));
    }

    public final double getRatio() {
        return this.getWidth().doubleValue() / this.getHeight().doubleValue();
    }

    public final BigDecimal getX() {
        return LengthUnit.parseLength(this.getSVGAttr("x"), getUnit());
    }

    public final BigDecimal getY() {
        return LengthUnit.parseLength(this.getSVGAttr("y"), getUnit());
    }

    /**
     * The unit that all length methods use.
     * 
     * @return the unit used, eg "mm".
     */
    public final LengthUnit getUnit() {
        return LengthUnit.MM;
    }

    public final void addImage(final String name, final BytesProducer data) {
        final Element imageElem = new Element("image", getElement().getNamespace("draw"));
        this.getElement().addContent(imageElem);
        this.putImage(imageElem, name, data);
    }

    private final void putImage(final Element imageElem, final String name, final BytesProducer data) {
        final String imgPath = "Pictures/" + name + (data.getFormat() != null ? "." + data.getFormat() : "");
        imageElem.setAttribute("href", imgPath, this.getODDocument().getVersion().getNS("xlink"));
        this.getODDocument().getPackage().putFile(imgPath, data.getBytes(this));
    }

    public final void setImage(final String name, final BytesProducer data, final boolean allowAdd) {
        final Element imageElem = this.getElement().getChild("image", getElement().getNamespace("draw"));
        if (imageElem != null) {
            final String oldPath = imageElem.getAttributeValue("href", this.getODDocument().getVersion().getNS("xlink"));
            this.getODDocument().getPackage().putFile(oldPath, null);
            imageElem.removeChild("binary-data", this.getODDocument().getVersion().getOFFICE());

            if (data == null) {
                imageElem.detach();
            } else {
                this.putImage(imageElem, name, data);
            }
        } else if (data != null) {
            if (allowAdd)
                this.addImage(name, data);
            else
                throw new IllegalStateException("No image in " + this);
        }
    }
}
