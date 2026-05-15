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

package org.jopendocument.io.svm;

import java.io.IOException;

import org.jopendocument.io.LittleEndianInputStream;

public class HeaderParser {
    public static final int COMPRESSMODE_NONE = 0;
    public static final int COMPRESSMODE_ZBITMAP = 1;
    public static final int COMPRESSMODE_NATIVE = 16;

    private int version;
    private int length;
    private int compressionMode;
    private int width;
    private int height;
    private int actionCount;
    private MapNode mapNode;

    public HeaderParser(LittleEndianInputStream in) throws IOException {
        byte[] mcode = new byte[6];
        in.read(mcode);
        this.version = in.readUnsignedShort();
        this.length = in.readInt();
        this.compressionMode = in.readInt();
        this.mapNode = new MapNode(in);
        this.width = in.readInt();
        this.height = in.readInt();
        this.actionCount = in.readInt();
    }

    @Override
    public String toString() {
        return "Header: version:" + this.version + " size:" + this.width + "x" + this.height + " comp:" + this.compressionMode + " actions:" + this.actionCount + " lenght:" + this.length;
    }

    public MapNode getMapNode() {
        return this.mapNode;
    }

    public int getActionCount() {
        return this.actionCount;
    }

}
