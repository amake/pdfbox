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
import java.lang.Character.UnicodeScript;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private Set<String> supportedScripts;
    private Map<Integer, Integer> lookupCache = new HashMap<>();
    private Map<Integer, Integer> reverseLookup = new HashMap<>();

    private String lastUsedSupportedScript;

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

        supportedScripts = getSupportedScripts();
    }

    ScriptRecord[] readScriptList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int scriptCount = data.readUnsignedShort();
        ScriptRecord[] scriptRecords = new ScriptRecord[scriptCount];
        int[] scriptOffsets = new int[scriptCount];
        for (int i = 0; i < scriptCount; i++)
        {
            ScriptRecord scriptRecord = new ScriptRecord();
            scriptRecord.scriptTag = data.readString(4);
            scriptOffsets[i] = data.readUnsignedShort();
            scriptRecords[i] = scriptRecord;
        }
        for (int i = 0; i < scriptCount; i++)
        {
            scriptRecords[i].scriptTable = readScriptTable(data, offset + scriptOffsets[i]);
        }
        return scriptRecords;
    }

    ScriptTable readScriptTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        ScriptTable scriptTable = new ScriptTable();
        int defaultLangSys = data.readUnsignedShort();
        int langSysCount = data.readUnsignedShort();
        scriptTable.langSysRecords = new LangSysRecord[langSysCount];
        int[] langSysOffsets = new int[langSysCount];
        for (int i = 0; i < langSysCount; i++)
        {
            LangSysRecord langSysRecord = new LangSysRecord();
            langSysRecord.langSysTag = data.readString(4);
            langSysOffsets[i] = data.readUnsignedShort();
            scriptTable.langSysRecords[i] = langSysRecord;
        }
        if (defaultLangSys != 0)
        {
            scriptTable.defaultLangSysTable = readLangSysTable(data, offset + defaultLangSys);
        }
        for (int i = 0; i < langSysCount; i++)
        {
            scriptTable.langSysRecords[i].langSysTable = readLangSysTable(data,
                    offset + langSysOffsets[i]);
        }
        return scriptTable;
    }

    LangSysTable readLangSysTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        LangSysTable langSysTable = new LangSysTable();
        @SuppressWarnings("unused")
        int lookupOrder = data.readUnsignedShort();
        langSysTable.requiredFeatureIndex = data.readUnsignedShort();
        int featureIndexCount = data.readUnsignedShort();
        langSysTable.featureIndices = new int[featureIndexCount];
        for (int i = 0; i < featureIndexCount; i++)
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
        int[] featureOffsets = new int[featureCount];
        for (int i = 0; i < featureCount; i++)
        {
            FeatureRecord featureRecord = new FeatureRecord();
            featureRecord.featureTag = data.readString(4, StandardCharsets.US_ASCII);
            featureOffsets[i] = data.readUnsignedShort();
            featureRecords[i] = featureRecord;
        }
        for (int i = 0; i < featureCount; i++)
        {
            featureRecords[i].featureTable = readFeatureTable(data, offset + featureOffsets[i]);
        }
        return featureRecords;
    }

    FeatureTable readFeatureTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        FeatureTable featureTable = new FeatureTable();
        @SuppressWarnings("unused")
        int featureParams = data.readUnsignedShort();
        int lookupIndexCount = data.readUnsignedShort();
        featureTable.lookupListIndices = new int[lookupIndexCount];
        for (int i = 0; i < lookupIndexCount; i++)
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
        int subTableCount = data.readUnsignedShort();
        int[] subTableOffets = new int[subTableCount];
        for (int i = 0; i < subTableCount; i++)
        {
            subTableOffets[i] = data.readUnsignedShort();
        }
        if ((lookupTable.lookupFlag & 0x0010) != 0)
        {
            lookupTable.markFilteringSet = data.readUnsignedShort();
        }
        lookupTable.subTables = new LookupSubTable[subTableCount];
        switch (lookupTable.lookupType)
        {
        case 1: // Single
            for (int i = 0; i < subTableCount; i++)
            {
                lookupTable.subTables[i] = readLookupSubTable(data, offset + subTableOffets[i]);
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
            int coverageOffset = data.readUnsignedShort();
            lookupSubTable.deltaGlyphID = data.readUnsignedShort();
            lookupSubTable.coverageTable = readCoverageTable(data, offset + coverageOffset);
            return lookupSubTable;
        }
        case 2:
        {
            LookupTypeSingleSubstFormat2 lookupSubTable = new LookupTypeSingleSubstFormat2();
            lookupSubTable.substFormat = substFormat;
            int coverageOffset = data.readUnsignedShort();
            int glyphCount = data.readUnsignedShort();
            lookupSubTable.substituteGlyphIDs = new int[glyphCount];
            for (int i = 0; i < glyphCount; i++)
            {
                lookupSubTable.substituteGlyphIDs[i] = data.readUnsignedShort();
            }
            lookupSubTable.coverageTable = readCoverageTable(data, offset + coverageOffset);
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
            int glyphCount = data.readUnsignedShort();
            coverageTable.glyphArray = new int[glyphCount];
            for (int i = 0; i < glyphCount; i++)
            {
                coverageTable.glyphArray[i] = data.readUnsignedShort();
            }
            return coverageTable;
        }
        case 2:
        {
            CoverageTableFormat2 coverageTable = new CoverageTableFormat2();
            coverageTable.coverageFormat = coverageFormat;
            int rangeCount = data.readUnsignedShort();
            coverageTable.rangeRecords = new RangeRecord[rangeCount];
            for (int i = 0; i < rangeCount; i++)
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

    private Set<String> getSupportedScripts()
    {
        Set<String> result = new HashSet<>(scriptList.length);
        for (ScriptRecord scriptRecord : scriptList)
        {
            result.add(scriptRecord.scriptTag);
        }
        return result;
    }

    /**
     * Get the OpenType script tag corresponding to the provided {@code UnicodeScript}.
     * @param script The {@code UnicodeScript}
     * @return The corresponding OpenType script tag
     */
    private String getScriptTag(UnicodeScript script)
    {
        String tag = scriptToTag(script);
        if (tag == SCRIPT_TAG_INHERITED
                || (tag == SCRIPT_TAG_DEFAULT && !supportedScripts.contains(tag)))
        {
            // We don't know what script this should be.
            if (lastUsedSupportedScript != null)
            {
                // Use past context
                return lastUsedSupportedScript;
            }
            else
            {
                // We have no past context and (currently) no way to get future context so we guess.
                tag = scriptList[0].scriptTag;
            }
        }
        if (supportedScripts.contains(tag))
        {
            lastUsedSupportedScript = tag;
        }
        return tag;
    }

    /**
     * Convert a {@code UnicodeScript} to an OpenType script tag. These are not necessarily the same as Unicode scripts.
     *
     * @param script
     * @return A four-letter script tag
     * @see <a href="https://www.microsoft.com/typography/otspec/scripttags.htm">Microsoft Typography: Script Tags</a>
     */
    private static String scriptToTag(UnicodeScript script)
    {
        switch (script)
        {
        // Adlam: adlm
        // Ahom: ahom
        // Anatolian Hieroglyphs: hluw
        case ARABIC:
            return "arab";
        case ARMENIAN:
            return "armn";
        case AVESTAN:
            return "avst";
        case BALINESE:
            return "bali";
        case BAMUM:
            return "bamu";
        // Bassa Vah: bass
        case BATAK:
            return "batk";
        case BENGALI:
            return "beng";
        // Bengali v.2: bng2
        // Bhaiksuki: bhks
        case BOPOMOFO:
            return "bopo";
        case BRAHMI:
            return "brah";
        case BRAILLE:
            return "brai";
        case BUGINESE:
            return "bugi";
        case BUHID:
            return "buhd";
        // Byzantine Music: byzm
        case CANADIAN_ABORIGINAL:
            return "cans";
        case CARIAN:
            return "cari";
        // Caucasian Albanian: aghb
        // Chakma: cakm
        case CHAM:
            return "cham";
        case CHEROKEE:
            return "cher";
        case COMMON: // "Default" in OpenType
            return SCRIPT_TAG_DEFAULT;
        case COPTIC:
            return "copt";
        case CUNEIFORM: // "Sumero-Akkadian Cuneiform" in OpenType
            return "xsux";
        case CYPRIOT:
            return "cprt";
        case CYRILLIC:
            return "cyrl";
        case DESERET:
            return "dsrt";
        case DEVANAGARI:
            return "deva";
        // Devanagari v.2: dev2
        // Duployan: dupl
        case EGYPTIAN_HIEROGLYPHS:
            return "egyp";
        // Elbasan: elba
        case ETHIOPIC:
            return "ethi";
        case GEORGIAN:
            return "geor";
        case GLAGOLITIC:
            return "glag";
        case GOTHIC:
            return "goth";
        // Grantha: gran
        case GREEK:
            return "grek";
        case GUJARATI:
            return "gujr";
        // Gujarati v.2: gjr2
        case GURMUKHI:
            return "guru";
        // Gurmukhi v.2: gur2
        case HAN: // "CJK Ideographic" in OpenType
            return "hani";
        case HANGUL:
            return "hang";
        // Hangul Jamo: jamo
        case HANUNOO:
            return "hano";
        // Hatran: hatr
        case HEBREW:
            return "hebr";
        case HIRAGANA:
            return "kana";
        case IMPERIAL_ARAMAIC:
            return "armi";
        case INHERITED:
            return SCRIPT_TAG_INHERITED;
        case INSCRIPTIONAL_PAHLAVI:
            return "phli";
        case INSCRIPTIONAL_PARTHIAN:
            return "prti";
        case JAVANESE:
            return "java";
        case KAITHI:
            return "kthi";
        case KANNADA:
            return "knda";
        // Kannada v.2: knd2
        case KATAKANA:
            return "kana";
        case KAYAH_LI:
            return "kali";
        case KHAROSHTHI:
            return "khar";
        case KHMER:
            return "khmr";
        // Khojki: khoj
        // Khudawadi: sind
        case LAO:
            return "lao";
        case LATIN:
            return "latn";
        case LEPCHA:
            return "lepc";
        case LIMBU:
            return "limb";
        // Linear A: lina
        case LINEAR_B:
            return "linb";
        case LISU:
            return "lisu";
        case LYCIAN:
            return "lyci";
        case LYDIAN:
            return "lydi";
        // Mahajani: mahj
        case MALAYALAM:
            return "mlym";
        // Malayalam v.2: mlm2
        case MANDAIC:
            return "mand";
        // Manichaean: mani
        // Marchen: marc
        // Mathematical Alphanumeric Symbols: math
        case MEETEI_MAYEK:
            return "mtei";
        // Mende Kikakui: mend
        // Meroitic Cursive: merc
        // Meroitic Hieroglyphs: mero
        // Miao: plrd
        // Modi: modi
        case MONGOLIAN:
            return "mong";
        // Mro: mroo
        // Multani: mult
        // Musical Symbols: musc
        case MYANMAR:
            return "mymr";
        // Myanmar v.2: mym2
        // Nabataean: nbat
        // Newa: newa
        case NEW_TAI_LUE:
            return "talu";
        case NKO:
            return "nko ";
        case OGHAM:
            return "ogam";
        case OL_CHIKI:
            return "olck";
        case OLD_ITALIC:
            return "ital";
        // Old Hungarian: hung
        // Old North Arabian: narb
        // Old Permic: perm
        case OLD_PERSIAN:
            return "xpeo";
        case OLD_SOUTH_ARABIAN:
            return "sarb";
        case OLD_TURKIC:
            return "orkh";
        case ORIYA: // "Odia (formerly Oriya)"
            return "orya";
        // Odia v.2 (formerly Oriya v.2): ory2
        // Osage: osge
        case OSMANYA:
            return "osma";
        // Pahawh Hmong: hmng
        // Palmyrene: palm
        // Pau Cin Hau: pauc
        case PHAGS_PA:
            return "phag";
        case PHOENICIAN:
            return "phnx";
        // Psalter Pahlavi: phlp
        case REJANG:
            return "rjng";
        case RUNIC:
            return "runr";
        case SAMARITAN:
            return "samr";
        case SAURASHTRA:
            return "saur";
        // Sharada: shrd
        case SHAVIAN:
            return "shaw";
        // Siddham: sidd
        // Sign Writing: sgnw
        case SINHALA:
            return "sinh";
        // Sora Sompeng: sora
        case SUNDANESE:
            return "sund";
        case SYLOTI_NAGRI:
            return "sylo";
        case SYRIAC:
            return "syrc";
        case TAGALOG:
            return "tglg";
        case TAGBANWA:
            return "tagb";
        case TAI_LE:
            return "tale";
        case TAI_THAM:
            return "lana";
        case TAI_VIET:
            return "tavt";
        // Takri: takr
        case TAMIL:
            return "taml";
        // Tamil v.2: tml2
        // Tangut: tang
        case TELUGU:
            return "telu";
        // Telugu v.2: tel2
        case THAANA:
            return "thaa";
        case THAI:
            return "thai";
        case TIBETAN:
            return "tibt";
        case TIFINAGH:
            return "tfng";
        // Tirhuta: tirh
        case UGARITIC:
            return "ugar";
        case UNKNOWN:
            return SCRIPT_TAG_DEFAULT;
        case VAI:
            return "vai ";
        // Warang Citi: wara
        case YI:
            return "yi  ";
        default:
            return SCRIPT_TAG_DEFAULT;
        }
    }

    private List<LangSysTable> getLangSysTables(String scriptTag)
    {
        List<LangSysTable> result = new ArrayList<>();
        for (ScriptRecord scriptRecord : scriptList)
        {
            if (scriptRecord.scriptTag.equals(scriptTag))
            {
                LangSysTable def = scriptRecord.scriptTable.defaultLangSysTable;
                if (def != null)
                {
                    result.add(def);
                }
                for (LangSysRecord langSysRecord : scriptRecord.scriptTable.langSysRecords)
                {
                    result.add(langSysRecord.langSysTable);
                }
            }
        }
        return result;
    }

    /**
     * Get a list of {@code FeatureRecord}s from a collection of {@code LangSysTable}s. Optionally filter the returned
     * features by supplying a list of allowed feature tags in {@code enabledFeatures}.
     *
     * Note that features listed as required ({@code LangSysTable#requiredFeatureIndex}) will be included even if not
     * explicitly enabled.
     *
     * @param langSysTables The {@code LangSysTable}s indicating {@code FeatureRecord}s to search for
     * @param enabledFeatures An optional whitelist of feature tags ({@code null} to allow all)
     * @return The indicated {@code FeatureRecord}s
     */
    private List<FeatureRecord> getFeatureRecords(List<LangSysTable> langSysTables,
            Collection<String> enabledFeatures)
    {
        List<FeatureRecord> result = new ArrayList<>();
        for (LangSysTable langSysTable : langSysTables)
        {
            int required = langSysTable.requiredFeatureIndex;
            if (required != 0xffff) // if no required features = 0xFFFF
            {
                result.add(featureList[required]);
            }
            for (int featureIndex : langSysTable.featureIndices)
            {
                if (featureIndex >= 0 && featureIndex < featureList.length)
                {
                    if (enabledFeatures == null
                            || enabledFeatures.contains(featureList[featureIndex].featureTag))
                    {
                        result.add(featureList[featureIndex]);
                    }
                }
            }
        }
        return result;
    }

    private List<LookupTable> getLookupTables(List<FeatureRecord> featureRecords)
    {
        List<LookupTable> result = new ArrayList<>();
        for (FeatureRecord featureRecord : featureRecords)
        {
            for (int lookupListIndex : featureRecord.featureTable.lookupListIndices)
            {
                if (lookupListIndex >= 0 && lookupListIndex < lookupList.length)
                {
                    result.add(lookupList[lookupListIndex]);
                }
            }
        }
        return result;
    }

    private int doLookup(LookupTable lookupTable, int gid)
    {
        for (LookupSubTable lookupSubtable : lookupTable.subTables)
        {
            int coverageIndex = lookupSubtable.coverageTable.getCoverageIndex(gid);
            if (coverageIndex >= 0)
            {
                return lookupSubtable.doSubstitution(gid, coverageIndex);
            }
        }
        return gid;
    }

    public int getSubstitution(int gid, UnicodeScript script, Collection<String> enabledFeatures)
    {
        if (gid == -1)
        {
            return -1;
        }
        Integer cached = lookupCache.get(gid);
        if (cached != null)
        {
            // Because script detection for indeterminate scripts (COMMON, INHERIT, etc.) depends on context,
            // it is possible to return a different substitution for the same input. However we don't want that,
            // as we need a one-to-one mapping.
            return cached;
        }
        List<LangSysTable> langSysTables = getLangSysTables(getScriptTag(script));
        if (langSysTables.isEmpty())
        {
            return gid;
        }
        List<FeatureRecord> featureRecords = getFeatureRecords(langSysTables, enabledFeatures);
        if (featureRecords.isEmpty())
        {
            return gid;
        }
        List<LookupTable> lookupTables = getLookupTables(featureRecords);
        for (LookupTable lookupTable : lookupTables)
        {
            if (lookupTable.lookupType == 1)
            {
                int sgid = doLookup(lookupTable, gid);
                lookupCache.put(gid, sgid);
                reverseLookup.put(sgid, gid);
                return sgid;
            }
        }
        return gid;
    }

    public int getUnsubstitution(int sgid)
    {
        Integer gid = reverseLookup.get(sgid);
        if (gid == null)
        {
            throw new IllegalArgumentException(
                    "Trying to un-substitute a never-before-seen gid: " + sgid);
        }
        return gid;
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
        ScriptTable scriptTable;

        @Override
        public String toString()
        {
            return String.format("ScriptRecord[scriptTag=%s]", scriptTag);
        }
    }

    static class ScriptTable
    {
        LangSysTable defaultLangSysTable;
        LangSysRecord[] langSysRecords;

        @Override
        public String toString()
        {
            return String.format("ScriptTable[hasDefault=%s,langSysRecordsCount=%d]",
                    defaultLangSysTable != null, langSysRecords.length);
        }
    }

    static class LangSysRecord
    {
        // https://www.microsoft.com/typography/otspec/languagetags.htm
        String langSysTag;
        LangSysTable langSysTable;

        @Override
        public String toString()
        {
            return String.format("LangSysRecord[langSysTag=%s]", langSysTag);
        }
    }

    static class LangSysTable
    {
        int requiredFeatureIndex;
        int[] featureIndices;

        @Override
        public String toString()
        {
            return String.format("LangSysTable[requiredFeatureIndex=%d]", requiredFeatureIndex);
        }
    }

    static class FeatureRecord
    {
        String featureTag;
        FeatureTable featureTable;

        @Override
        public String toString()
        {
            return String.format("FeatureRecord[featureTag=%s]", featureTag);
        }
    }

    static class FeatureTable
    {
        int[] lookupListIndices;

        @Override
        public String toString()
        {
            return String.format("FeatureTable[lookupListIndiciesCount=%d]",
                    lookupListIndices.length);
        }
    }

    static class LookupTable
    {
        int lookupType;
        int lookupFlag;
        int markFilteringSet;
        LookupSubTable[] subTables;

        @Override
        public String toString()
        {
            return String.format("LookupTable[lookupType=%d,lookupFlag=%d,markFilteringSet=%d]",
                    lookupType, lookupFlag, markFilteringSet);
        }
    }

    static abstract class LookupSubTable
    {
        int substFormat;
        CoverageTable coverageTable;

        abstract int doSubstitution(int gid, int coverageIndex);
    }

    static class LookupTypeSingleSubstFormat1 extends LookupSubTable
    {
        int deltaGlyphID;

        @Override
        int doSubstitution(int gid, int coverageIndex)
        {
            return coverageIndex < 0 ? gid : gid + deltaGlyphID;
        }

        @Override
        public String toString()
        {
            return String.format("LookupTypeSingleSubstFormat1[substFormat=%d,deltaGlyphID=%d]",
                    substFormat, deltaGlyphID);
        }
    }

    static class LookupTypeSingleSubstFormat2 extends LookupSubTable
    {
        int[] substituteGlyphIDs;

        @Override
        int doSubstitution(int gid, int coverageIndex)
        {
            return coverageIndex < 0 ? gid : substituteGlyphIDs[coverageIndex];
        }

        @Override
        public String toString()
        {
            return String.format(
                    "LookupTypeSingleSubstFormat2[substFormat=%d,substituteGlyphIDs=%s]",
                    substFormat, Arrays.toString(substituteGlyphIDs));
        }
    }

    static abstract class CoverageTable
    {
        int coverageFormat;

        abstract int getCoverageIndex(int gid);
    }

    static class CoverageTableFormat1 extends CoverageTable
    {
        int[] glyphArray;

        @Override
        int getCoverageIndex(int gid)
        {
            return Arrays.binarySearch(glyphArray, gid);
        }

        @Override
        public String toString()
        {
            return String.format("CoverageTableFormat1[coverageFormat=%d,glyphArray=%s]",
                    coverageFormat, Arrays.toString(glyphArray));
        }
    }

    static class CoverageTableFormat2 extends CoverageTable
    {
        RangeRecord[] rangeRecords;

        @Override
        int getCoverageIndex(int gid)
        {
            for (RangeRecord rangeRecord : rangeRecords)
            {
                if (rangeRecord.startGlyphID <= gid && gid <= rangeRecord.endGlyphID)
                {
                    return rangeRecord.startCoverageIndex + gid - rangeRecord.startGlyphID;
                }
            }
            return -1;
        }

        @Override
        public String toString()
        {
            return String.format("CoverageTableFormat2[coverageFormat=%d]", coverageFormat);
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
