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

import org.jopendocument.dom.ODNode;

import java.util.List;
import java.util.Objects;

import org.jdom.Element;

public final class RepeatedRange {

    static RepeatedRange create(final List<? extends ODNode> children, final int col, final String attrName) {
        final ODNode c = children.get(col);
        final Element element = c.getElement();

        final String repeatedS = element.getAttributeValue(attrName, element.getNamespace());
        final int firstIndex, lastIndex;
        if (repeatedS == null) {
            firstIndex = col;
            lastIndex = col;
        } else {
            final int repeated = Integer.parseInt(repeatedS);
            // since for repeated elements, the same instance is at multiple index
            firstIndex = children.indexOf(c);
            lastIndex = firstIndex + repeated - 1;
            assert children.get(lastIndex) == c;
        }
        final int indexOfElement = element.getParent().indexOf(element);

        return new RepeatedRange(indexOfElement, firstIndex, lastIndex);
    }

    static public final RepeatedRange createSingle(final int indexOfElement, final int logicalIndex) {
        return new RepeatedRange(indexOfElement, logicalIndex, logicalIndex);
    }

    private final int firstIndex, lastIndex;
    // not the same as firstIndex as they're can be repeated elements before
    private final int indexOfElement;

    public RepeatedRange(int indexOfElement, int firstIndex, int lastIndex) {
        super();
        if (firstIndex < 0 || lastIndex < 0 || indexOfElement < 0)
            throw new IllegalArgumentException("Negative index");
        if (firstIndex > lastIndex)
            throw new IllegalArgumentException("Invalid indexes : " + firstIndex + " > " + lastIndex);
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.indexOfElement = indexOfElement;
    }

    public final int getFirstIndex() {
        return this.firstIndex;
    }

    public final int getLastIndex() {
        return this.lastIndex;
    }

    public final int getRepeated() {
        return this.getLastIndex() - this.getFirstIndex() + 1;
    }

    public final int getIndexOfElement() {
        return this.indexOfElement;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.firstIndex, this.indexOfElement, this.lastIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final RepeatedRange other = (RepeatedRange) obj;
        return this.firstIndex == other.firstIndex && this.indexOfElement == other.indexOfElement && this.lastIndex == other.lastIndex;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " XML #" + this.getIndexOfElement() + " [" + this.getFirstIndex() + ',' + this.getLastIndex() + "]";
    }
}
