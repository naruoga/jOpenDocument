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

import java.util.Date;

public class DateToSQLConvertor extends NullIsNullConvertor<Date, java.sql.Date> {

    @Override
    protected java.sql.Date convertNonNull(Date o) {
        return new java.sql.Date(o.getTime());
    }

    @Override
    protected Date unconvertNonNull(java.sql.Date o) {
        return o;
    }
}
