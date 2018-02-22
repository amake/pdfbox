/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.font;

import java.io.IOException;

import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;

public class PDCIDFontType0Embedder extends PDCIDFontType2Embedder
{

    PDCIDFontType0Embedder(PDDocument document, COSDictionary dict, TrueTypeFont ttf,
            boolean embedSubset, PDType0Font parent, boolean vertical) throws IOException
    {
        super(document, dict, ttf, embedSubset, parent, vertical);
    }

    @Override
    protected int getVerticalOrigin(int cid) throws IOException
    {
        return 1000;
    }

}
