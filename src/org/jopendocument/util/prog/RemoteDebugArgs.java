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

package org.jopendocument.util.prog;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RemoteDebugArgs {

    private static final DateFormat DBG_TIME_FORMAT = new SimpleDateFormat("HHmmss");

    /**
     * Print VM debug args on stdout if the system property remoteDebug is set to "true". The port
     * will be set to the first 5 digits of the current time (eg 17254 for 17h25m43s)
     * 
     * @param args not used.
     */
    public static void main(String[] args) {
        final String debugArgs = Boolean.getBoolean("remoteDebug") ? getArgs(System.getProperty("remoteDebugAddr")) : "";

        // do not output newline to avoid problem on cygwin (\r)
        System.out.print(debugArgs);
    }

    public static String getArgs(String prop) {
        final String debugAddr;
        if (prop == null || prop.trim().isEmpty()) {
            final Date now = new Date();
            String time = DBG_TIME_FORMAT.format(now);
            if (time.startsWith("00"))
                time = "24" + time.substring(2);
            else if (time.startsWith("01"))
                time = "25" + time.substring(2);
            debugAddr = time.substring(0, time.length() - 1);
        } else {
            debugAddr = prop.trim();
        }
        return "-agentlib:jdwp=transport=dt_socket,address=" + debugAddr + ",server=y,suspend=n";
    }
}
