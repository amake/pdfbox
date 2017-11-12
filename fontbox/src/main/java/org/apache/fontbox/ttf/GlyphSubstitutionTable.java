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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A glyph substitution 'GSUB' table in a TrueType or OpenType font.
 *
 * @author Aaron Madlon-Kay
 */
public class GlyphSubstitutionTable extends TTFTable
{

    private static final Log LOG = LogFactory.getLog(GlyphSubstitutionTable.class);

    public static final String TAG = "GSUB";

    private static final String SCRIPT_TAG_INHERITED = "<inherited>";
    private static final String SCRIPT_TAG_DEFAULT = "DFLT";

    private ScriptRecord[] scriptList;
    private FeatureRecord[] featureList;
    private LookupTable[] lookupList;

    GlyphSubstitutionTable(TrueTypeFont font)
    {
        super(font);
    }

    @Override
    void read(TrueTypeFont ttf, TTFDataStream data) throws IOException
    {
        long start = data.getCurrentPosition();
        @SuppressWarnings("unused")
        int majorVersion = data.readUnsignedShort();
        int minorVersion = data.readUnsignedShort();
        int scriptListOffset = data.readUnsignedShort();
        int featureListOffset = data.readUnsignedShort();
        int lookupListOffset = data.readUnsignedShort();
        @SuppressWarnings("unused")
        long featureVariationsOffset = -1L;
        if (minorVersion == 1L)
        {
            featureVariationsOffset = data.readUnsignedInt();
        }

        scriptList = readScriptList(data, start + scriptListOffset);
        featureList = readFeatureList(data, start + featureListOffset);
        lookupList = readLookupList(data, start + lookupListOffset);
        System.out.println();
    }

    ScriptRecord[] readScriptList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int scriptCount = data.readUnsignedShort();
        ScriptRecord[] scriptRecords = new ScriptRecord[scriptCount];
        System.out.println("Script records: " + scriptCount);
        for (int i = 0; i < scriptCount; i++)
        {
            ScriptRecord scriptRecord = new ScriptRecord();
            scriptRecord.scriptTag = data.readString(4);
            System.out.println("Script: " + scriptRecord.scriptTag);
            scriptRecord.scriptOffset = data.readUnsignedShort();
            scriptRecords[i] = scriptRecord;
        }
        for (int i = 0; i < scriptCount; i++)
        {
            scriptRecords[i].scriptTable = readScriptTable(data,
                    offset + scriptRecords[i].scriptOffset);
        }
        return scriptRecords;
    }

    ScriptTable readScriptTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        ScriptTable scriptTable = new ScriptTable();
        scriptTable.defaultLangSys = data.readUnsignedShort();
        scriptTable.langSysCount = data.readUnsignedShort();
        System.out.println("LangSys records: " + scriptTable.langSysCount);
        scriptTable.langSysRecords = new LangSysRecord[scriptTable.langSysCount];
        for (int i = 0; i < scriptTable.langSysCount; i++)
        {
            LangSysRecord langSysRecord = new LangSysRecord();
            langSysRecord.langSysTag = data.readString(4);
            System.out.println("LangSys tag: " + langSysRecord.langSysTag);
            langSysRecord.langSysOffset = data.readUnsignedShort();
            scriptTable.langSysRecords[i] = langSysRecord;
        }
        if (scriptTable.defaultLangSys != 0)
        {
            scriptTable.defaultLangSysTable = readLangSysTable(data,
                    offset + scriptTable.defaultLangSys);
        }
        for (int i = 0; i < scriptTable.langSysCount; i++)
        {
            scriptTable.langSysRecords[i].langSysTable = readLangSysTable(data,
                    offset + scriptTable.langSysRecords[i].langSysOffset);
        }
        return scriptTable;
    }

    LangSysTable readLangSysTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        LangSysTable langSysTable = new LangSysTable();
        langSysTable.lookupOrder = data.readUnsignedShort();
        langSysTable.requiredFeatureIndex = data.readUnsignedShort();
        langSysTable.featureIndexCount = data.readUnsignedShort();
        System.out.println("Feature index count: " + langSysTable.featureIndexCount);
        langSysTable.featureIndices = new int[langSysTable.featureIndexCount];
        for (int i = 0; i < langSysTable.featureIndexCount; i++)
        {
            langSysTable.featureIndices[i] = data.readUnsignedShort();
        }
        return langSysTable;
    }

    FeatureRecord[] readFeatureList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int featureCount = data.readUnsignedShort();
        FeatureRecord[] featureRecords = new FeatureRecord[featureCount];
        for (int i = 0; i < featureCount; i++)
        {
            FeatureRecord featureRecord = new FeatureRecord();
            featureRecord.featureTag = data.readString(4, StandardCharsets.US_ASCII);
            System.out.println("Feature: " + featureRecord.featureTag);
            featureRecord.featureOffset = data.readUnsignedShort();
            featureRecords[i] = featureRecord;
        }
        for (int i = 0; i < featureCount; i++)
        {
            featureRecords[i].featureTable = readFeatureTable(data,
                    offset + featureRecords[i].featureOffset);
        }
        return featureRecords;
    }

    FeatureTable readFeatureTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        FeatureTable featureTable = new FeatureTable();
        featureTable.featureParams = data.readUnsignedShort();
        featureTable.lookupIndexCount = data.readUnsignedShort();
        featureTable.lookupListIndices = new int[featureTable.lookupIndexCount];
        for (int i = 0; i < featureTable.lookupIndexCount; i++)
        {
            featureTable.lookupListIndices[i] = data.readUnsignedShort();
        }
        return featureTable;
    }

    LookupTable[] readLookupList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int lookupCount = data.readUnsignedShort();
        int[] lookups = new int[lookupCount];
        for (int i = 0; i < lookupCount; i++)
        {
            lookups[i] = data.readUnsignedShort();
        }
        LookupTable[] lookupTables = new LookupTable[lookupCount];
        for (int i = 0; i < lookupCount; i++)
        {
            lookupTables[i] = readLookupTable(data, offset + lookups[i]);
        }
        return lookupTables;
    }

    LookupTable readLookupTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        LookupTable lookupTable = new LookupTable();
        lookupTable.lookupType = data.readUnsignedShort();
        lookupTable.lookupFlag = data.readUnsignedShort();
        lookupTable.subTableCount = data.readUnsignedShort();
        lookupTable.subTableOffets = new int[lookupTable.subTableCount];
        for (int i = 0; i < lookupTable.subTableCount; i++)
        {
            lookupTable.subTableOffets[i] = data.readUnsignedShort();
        }
        if ((lookupTable.lookupFlag & 0x0010) != 0)
        {
            lookupTable.markFilteringSet = data.readUnsignedShort();
        }
        System.out.println("Lookup type: " + lookupTable.lookupType);
        lookupTable.subTables = new LookupSubTable[lookupTable.subTableCount];
        switch (lookupTable.lookupType)
        {
        case 1: // Single
            for (int i = 0; i < lookupTable.subTableCount; i++)
            {
                lookupTable.subTables[i] = readLookupSubTable(data,
                        offset + lookupTable.subTableOffets[i]);
            }
            break;
        default:
            // Other lookup types are not supported
            LOG.debug("Type " + lookupTable.lookupType + " GSUB lookup table is not supported and will be ignored");
        }
        return lookupTable;
    }

    LookupSubTable readLookupSubTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int substFormat = data.readUnsignedShort();
        switch (substFormat)
        {
        case 1:
        {
            LookupTypeSingleSubstFormat1 lookupSubTable = new LookupTypeSingleSubstFormat1();
            lookupSubTable.substFormat = substFormat;
            lookupSubTable.coverageOffset = data.readUnsignedShort();
            lookupSubTable.deltaGlyphID = data.readUnsignedShort();
            lookupSubTable.coverageTable = readCoverageTable(data,
                    offset + lookupSubTable.coverageOffset);
            return lookupSubTable;
        }
        case 2:
        {
            LookupTypeSingleSubstFormat2 lookupSubTable = new LookupTypeSingleSubstFormat2();
            lookupSubTable.substFormat = substFormat;
            lookupSubTable.coverageOffset = data.readUnsignedShort();
            lookupSubTable.glyphCount = data.readUnsignedShort();
            lookupSubTable.substituteGlyphIDs = new int[lookupSubTable.glyphCount];
            for (int i = 0; i < lookupSubTable.glyphCount; i++)
            {
                lookupSubTable.substituteGlyphIDs[i] = data.readUnsignedShort();
            }
            lookupSubTable.coverageTable = readCoverageTable(data,
                    offset + lookupSubTable.coverageOffset);
            return lookupSubTable;
        }
        default:
            throw new IllegalArgumentException("Unknown substFormat: " + substFormat);
        }
    }

    CoverageTable readCoverageTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int coverageFormat = data.readUnsignedShort();
        switch (coverageFormat)
        {
        case 1:
        {
            CoverageTableFormat1 coverageTable = new CoverageTableFormat1();
            coverageTable.coverageFormat = coverageFormat;
            coverageTable.glyphCount = data.readUnsignedShort();
            coverageTable.glyphArray = new int[coverageTable.glyphCount];
            for (int i = 0; i < coverageTable.glyphCount; i++)
            {
                coverageTable.glyphArray[i] = data.readUnsignedShort();
            }
            return coverageTable;
        }
        case 2:
        {
            CoverageTableFormat2 coverageTable = new CoverageTableFormat2();
            coverageTable.coverageFormat = coverageFormat;
            coverageTable.rangeCount = data.readUnsignedShort();
            coverageTable.rangeRecords = new RangeRecord[coverageTable.rangeCount];
            for (int i = 0; i < coverageTable.rangeCount; i++)
            {
                coverageTable.rangeRecords[i] = readRangeRecord(data);
            }
            return coverageTable;

        }
        default:
            // Should not happen (the spec indicates only format 1 and format 2)
            throw new IllegalArgumentException("Unknown coverage format: " + coverageFormat);
        }
    }

    RangeRecord readRangeRecord(TTFDataStream data) throws IOException
    {
        RangeRecord rangeRecord = new RangeRecord();
        rangeRecord.startGlyphID = data.readUnsignedShort();
        rangeRecord.endGlyphID = data.readUnsignedShort();
        rangeRecord.startCoverageIndex = data.readUnsignedShort();
        return rangeRecord;
    }

    static class ScriptRecord
    {
        // https://www.microsoft.com/typography/otspec/scripttags.htm
        String scriptTag;
        int scriptOffset;
        ScriptTable scriptTable;

        @Override
        public String toString()
        {
            return String.format("ScriptRecord[scriptTag=%s,scriptOffset=%d]", scriptTag,
                    scriptOffset);
        }
    }

    static class ScriptTable
    {
        int defaultLangSys;
        int langSysCount;
        LangSysTable defaultLangSysTable;
        LangSysRecord[] langSysRecords;

        @Override
        public String toString()
        {
            return String.format("ScriptTable[defaultLangSys=%d,langSysCount=%d]", defaultLangSys,
                    langSysCount);
        }
    }

    static class LangSysRecord
    {
        // https://www.microsoft.com/typography/otspec/languagetags.htm
        String langSysTag;
        int langSysOffset;
        LangSysTable langSysTable;

        @Override
        public String toString()
        {
            return String.format("LangSysRecord[langSysTag=%s,langSysOffset=%d]", langSysTag,
                    langSysOffset);
        }
    }

    static class LangSysTable
    {
        int lookupOrder;
        int requiredFeatureIndex;
        int featureIndexCount;
        int[] featureIndices;

        @Override
        public String toString()
        {
            return String.format(
                    "LangSysTable[lookupOrder=%d,requiredFeatureIndex=%d,featureIndexCount=%d]",
                    lookupOrder, requiredFeatureIndex, featureIndexCount);
        }
    }

    static class FeatureRecord
    {
        String featureTag;
        int featureOffset;
        FeatureTable featureTable;

        @Override
        public String toString()
        {
            return String.format("FeatureRecord[featureTag=%s,featureOffset=%d]", featureTag,
                    featureOffset);
        }
    }

    static class FeatureTable
    {
        int featureParams;
        int lookupIndexCount;
        int[] lookupListIndices;

        @Override
        public String toString()
        {
            return String.format("FeatureTable[featureParams=%s,lookupIndexCount=%d]",
                    featureParams, lookupIndexCount);
        }
    }

    static class LookupTable
    {
        int lookupType;
        int lookupFlag;
        int subTableCount;
        int[] subTableOffets;
        int markFilteringSet;
        LookupSubTable[] subTables;

        @Override
        public String toString()
        {
            return String.format(
                    "LookupTable[lookupType=%d,lookupFlag=%d,subTableCount=%d,markFilteringSet=%d]",
                    lookupType, lookupFlag, subTableCount, markFilteringSet);
        }
    }

    static abstract class LookupSubTable
    {
        int substFormat;
        int coverageOffset;
        CoverageTable coverageTable;
    }

    static class LookupTypeSingleSubstFormat1 extends LookupSubTable
    {
        int deltaGlyphID;

        @Override
        public String toString()
        {
            return String.format(
                    "LookupTypeSingleSubstFormat1[substFormat=%d,coverageOffset=%d,deltaGlyphID=%d]",
                    substFormat, coverageOffset, deltaGlyphID);
        }
    }

    static class LookupTypeSingleSubstFormat2 extends LookupSubTable
    {
        int glyphCount;
        int[] substituteGlyphIDs;

        @Override
        public String toString()
        {
            return String.format(
                    "LookupTypeSingleSubstFormat2[substFormat=%d,coverageOffset=%d,deltaGlyphID=%d,substituteGlyphIDs=%s]",
                    substFormat, coverageOffset, glyphCount, Arrays.toString(substituteGlyphIDs));
        }
    }

    static abstract class CoverageTable
    {
        int coverageFormat;
    }

    static class CoverageTableFormat1 extends CoverageTable
    {
        int glyphCount;
        int[] glyphArray;

        @Override
        public String toString()
        {
            return String.format(
                    "CoverageTableFormat1[coverageFormat=%d,glyphCount=%d,glyphArray=%s]",
                    coverageFormat, glyphCount, Arrays.toString(glyphArray));
        }
    }

    static class CoverageTableFormat2 extends CoverageTable
    {
        int rangeCount;
        RangeRecord[] rangeRecords;

        @Override
        public String toString()
        {
            return String.format("CoverageTableFormat2[coverageFormat=%d,rangeCount=%d]",
                    coverageFormat, rangeCount);
        }
    }

    static class RangeRecord
    {
        int startGlyphID;
        int endGlyphID;
        int startCoverageIndex;

        @Override
        public String toString()
        {
            return String.format("RangeRecord[startGlyphID=%d,endGlyphID=%d,startCoverageIndex=%d]",
                    startGlyphID, endGlyphID, startCoverageIndex);
        }
    }
}
