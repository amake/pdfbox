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
package org.apache.fontbox.ttf;

import java.util.List;

public class SubstitutingCmapSubtable extends CmapSubtable
{

    private final CmapSubtable cmap;
    private final GlyphSubstitutionTable gsub;

    public SubstitutingCmapSubtable(CmapSubtable cmap, GlyphSubstitutionTable gsub)
    {
        this.cmap = cmap;
        this.gsub = gsub;
    }

    /**
     * @return Returns the platformEncodingId.
     */
    public int getPlatformEncodingId()
    {
        return cmap.getPlatformEncodingId();
    }

    /**
     * @param platformEncodingIdValue The platformEncodingId to set.
     */
    public void setPlatformEncodingId(int platformEncodingIdValue)
    {
        cmap.setPlatformEncodingId(platformEncodingIdValue);
    }

    /**
     * @return Returns the platformId.
     */
    public int getPlatformId()
    {
        return cmap.getPlatformId();
    }

    /**
     * @param platformIdValue The platformId to set.
     */
    public void setPlatformId(int platformIdValue)
    {
        cmap.setPlatformId(platformIdValue);
    }

    @Override
    public int getGlyphId(int characterCode)
    {
        return gsub.getVertSubstitution(cmap.getGlyphId(characterCode));
    }

    @Deprecated
    public Integer getCharacterCode(int gid)
    {
        return cmap.getCharacterCode(gsub.getVertUnsubstitution(gid));
    }

    public List<Integer> getCharCodes(int gid)
    {
        return cmap.getCharCodes(gsub.getVertUnsubstitution(gid));
    }
}
