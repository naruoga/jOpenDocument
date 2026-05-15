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

package org.jopendocument.util.convertor;

public class ReverseConvertor<T, U> implements ValueConvertor<T, U> {

    // avoid illegal new ReverseConvertor<?,?>()
    public static final <T, U> ReverseConvertor<T, U> create(ValueConvertor<U, T> o) {
        return new ReverseConvertor<>(o);
    }

    private final ValueConvertor<U, T> delegate;

    public ReverseConvertor(ValueConvertor<U, T> delegate) {
        super();
        this.delegate = delegate;
    }

    public U convert(T o) {
        return this.delegate.unconvert(o);
    }

    public T unconvert(U o) {
        return this.delegate.convert(o);
    }

}
