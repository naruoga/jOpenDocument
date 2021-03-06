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

package org.jopendocument.util.cc;

public class ITransformerWrapper<E, T> extends Transformer<E, T> {

    private final org.apache.commons.collections4.Transformer transf;

    public ITransformerWrapper(final org.apache.commons.collections4.Transformer transf) {
        super();
        this.transf = transf;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T transformChecked(E input) {
        return (T) this.transf.transform(input);
    }

}
