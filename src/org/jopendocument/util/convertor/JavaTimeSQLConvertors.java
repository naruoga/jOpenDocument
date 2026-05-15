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

public class JavaTimeSQLConvertors {

    static public ValueConvertor<java.time.LocalTime, java.sql.Time> LOCAL_TIME = new NullIsNullConvertor<java.time.LocalTime, java.sql.Time>() {
        @Override
        protected java.sql.Time convertNonNull(java.time.LocalTime o) {
            return java.sql.Time.valueOf(o);
        }

        @Override
        protected java.time.LocalTime unconvertNonNull(java.sql.Time o) {
            return o.toLocalTime();
        }
    };
    static public ValueConvertor<java.time.LocalDate, java.sql.Date> LOCAL_DATE = new NullIsNullConvertor<java.time.LocalDate, java.sql.Date>() {
        @Override
        protected java.sql.Date convertNonNull(java.time.LocalDate o) {
            return java.sql.Date.valueOf(o);
        }

        @Override
        protected java.time.LocalDate unconvertNonNull(java.sql.Date o) {
            return o.toLocalDate();
        }
    };
    static public ValueConvertor<java.time.LocalDateTime, java.sql.Timestamp> LOCAL_DATETIME = new NullIsNullConvertor<java.time.LocalDateTime, java.sql.Timestamp>() {
        @Override
        protected java.sql.Timestamp convertNonNull(java.time.LocalDateTime o) {
            return java.sql.Timestamp.valueOf(o);
        }

        @Override
        protected java.time.LocalDateTime unconvertNonNull(java.sql.Timestamp o) {
            return o.toLocalDateTime();
        }
    };

}
