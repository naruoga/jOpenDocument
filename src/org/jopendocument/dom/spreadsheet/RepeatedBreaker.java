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

package org.jopendocument.dom.spreadsheet;

import org.jopendocument.dom.ODDocument;
import org.jopendocument.dom.ODNode;
import org.jopendocument.dom.StyleProperties;
import org.jopendocument.util.ExceptionUtils;

import java.util.List;
import java.util.function.Consumer;

import org.jdom.Element;

abstract class RepeatedBreaker<P, C extends ODNode> {

    static final void setRepeated(final Element elem, final String attrName, final int i) {
        if (i < 1)
            throw new IllegalArgumentException("repeated <1 : " + i);
        if (i == 1)
            elem.removeAttribute(attrName, elem.getNamespace());
        else
            elem.setAttribute(attrName, String.valueOf(i), elem.getNamespace());
    }

    @SuppressWarnings("rawtypes")
    static private final RepeatedBreaker CELL_BREAKER = new RepeatedBreaker<Row<?>, Cell<?>>("number-columns-repeated") {
        @Override
        Cell<?> create(Element elem, Row<?> parent, int index, boolean single) {
            return createD(elem, parent, index, single);
        }

        <D extends ODDocument> Cell<D> createD(Element elem, Row<D> parent, int index, boolean single) {
            return parent.createCell(elem, parent.getSheet().getCellStyleDesc(), single);
        }
    };

    @SuppressWarnings("rawtypes")
    static private final RepeatedBreaker ROW_BREAKER = new RepeatedBreaker<Table<?>, Row<?>>(Axis.ROW.getRepeatedAttrName()) {
        @Override
        Row<?> create(Element elem, Table<?> parent, int index, boolean single) {
            return createD(elem, parent, index, single);
        }

        <D extends ODDocument> Row<D> createD(Element elem, Table<D> parent, int index, boolean single) {
            return new Row<D>(parent, elem, index, parent.getRowStyleDesc(), parent.getCellStyleDesc());
        }
    };

    @SuppressWarnings("unchecked")
    static final <D extends ODDocument> RepeatedBreaker<Row<D>, Cell<D>> getCellBreaker() {
        return (RepeatedBreaker<Row<D>, Cell<D>>) CELL_BREAKER;
    }

    @SuppressWarnings("unchecked")
    static final <D extends ODDocument> RepeatedBreaker<Table<D>, Row<D>> getRowBreaker() {
        return (RepeatedBreaker<Table<D>, Row<D>>) ROW_BREAKER;
    }

    private final String attrName;

    public RepeatedBreaker(final String attrName) {
        this.attrName = attrName;
    }

    public final String getAttributeName() {
        return this.attrName;
    }

    public final int getRepeated(final Element elem) {
        final String repeatedS = elem.getAttributeValue(this.getAttributeName(), elem.getNamespace());
        return StyleProperties.parseInt(repeatedS, 1);
    }

    abstract C create(final Element elem, final P parent, final int index, final boolean single);

    public final void breakRepeated(final P parent, final List<C> children, final int col) {
        this.breakRepeated(parent, children, col, true, 1, null);
    }

    public final int breakRepeated(final P parent, final List<C> children, final int col, final boolean absoluteOrMax, int count, final Consumer<? super C> elementModifier) {
        if (count == 0)
            throw new IllegalArgumentException("Zero count : " + count);
        final C c = children.get(col);
        final Element element = c.getElement();
        final RepeatedRange repState = RepeatedRange.create(children, col, this.getAttributeName());

        final int firstIndex = repState.getFirstIndex();
        final int lastIndex = repState.getLastIndex();
        int indexOfElement = repState.getIndexOfElement();

        final int initialCountFromCol = lastIndex - col + 1;
        if (count < 0)
            count = initialCountFromCol;
        else if (!absoluteOrMax)
            count = Math.min(count, initialCountFromCol);
        else if (count > initialCountFromCol)
            throw new IllegalArgumentException("Not enough repeated to break : " + count + " > " + initialCountFromCol);
        assert count >= 1;
        final int lastCol = col + count - 1;
        ExceptionUtils.requireSame(c, children.get(lastCol), "Not same repeated cell");

        final int preRepeated = col - firstIndex;
        final int postRepeated = lastIndex - lastCol;

        // start from the end to avoid changing indexOfElement
        breakRepeated(parent, children, element, indexOfElement, lastCol + 1, postRepeated, false);
        // clone element for preRepeated before changing it
        if (breakRepeated(parent, children, element, indexOfElement, firstIndex, preRepeated, true))
            indexOfElement++;
        assert element.getParent().getContent(indexOfElement) == element;

        element.removeAttribute(this.getAttributeName(), element.getNamespace());
        // modify element then call this.create() to create new ODNode
        if (elementModifier != null)
            elementModifier.accept(c);
        children.set(col, this.create(element, parent, col, true));
        for (int i = 1; i < count; i++) {
            final int index = col + i;
            children.set(index, this.create(cloneChild(element, indexOfElement + i), parent, index, true));
        }
        return count;
    }

    private final boolean breakRepeated(final P parent, final List<C> children, Element element, final int indexOfElement, int firstIndex, int repeat, boolean before) {
        final boolean res = repeat > 0;
        if (res) {
            assert element.getParent().getContent(indexOfElement) == element;
            final Element newElem = cloneChild(element, indexOfElement + (before ? 0 : 1));
            setRepeated(newElem, this.getAttributeName(), repeat);
            final C preCell = this.create(newElem, parent, firstIndex, false);
            for (int i = 0; i < repeat; i++) {
                children.set(firstIndex + i, preCell);
            }
        }
        return res;
    }

    private Element cloneChild(final Element element, final int addIndex) {
        final Element newElem = (Element) element.clone();
        element.getParentElement().addContent(addIndex, newElem);
        return newElem;
    }
}
