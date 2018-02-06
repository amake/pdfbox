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
import java.util.EnumMap;
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
        String[] tags = scriptToTags(script);
        if (tags.length == 1)
        {
            String tag = tags[0];
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
                    return lastUsedSupportedScript = scriptList[0].scriptTag;
                }
            }
        }
        for (String tag : tags)
        {
            if (supportedScripts.contains(tag))
            {
                // Use the first recognized tag. We assume a single font only recognizes one version ("ver. 2")
                // of a single script, or if it recognizes more than one that it prefers the latest one.
                return lastUsedSupportedScript = tag;
            }
        }
        return tags[0];
    }

    /**
     * A map associating {@code UnicodeScript}s with one or more OpenType script tags. Script tags are not necessarily
     * the same as Unicode scripts. A single Unicode script may correspond to multiple tags, especially when there has
     * been a revision to the latter (e.g. BENGALI -> [bng2, beng]). When there are multiple tags, they are ordered from
     * newest to oldest.
     *
     * @see <a href="https://www.microsoft.com/typography/otspec/scripttags.htm">Microsoft Typography: Script Tags</a>
     */
    private static final Map<UnicodeScript, String[]> SCRIPT_TO_TAGS;
    static
    {
        Map<UnicodeScript, String[]> map = new EnumMap<>(UnicodeScript.class);
        // Adlam: adlm
        // Ahom: ahom
        // Anatolian Hieroglyphs: hluw
        map.put(UnicodeScript.ARABIC, new String[] { "arab" });
        map.put(UnicodeScript.ARABIC, new String[] { "arab" });
        map.put(UnicodeScript.ARMENIAN, new String[] { "armn" });
        map.put(UnicodeScript.AVESTAN, new String[] { "avst" });
        map.put(UnicodeScript.BALINESE, new String[] { "bali" });
        map.put(UnicodeScript.BAMUM, new String[] { "bamu" });
        // Bassa Vah: bass
        map.put(UnicodeScript.BATAK, new String[] { "batk" });
        map.put(UnicodeScript.BENGALI, new String[] { "bng2", "beng" });
        // Bhaiksuki: bhks
        map.put(UnicodeScript.BOPOMOFO, new String[] { "bopo" });
        map.put(UnicodeScript.BRAHMI, new String[] { "brah" });
        map.put(UnicodeScript.BRAILLE, new String[] { "brai" });
        map.put(UnicodeScript.BUGINESE, new String[] { "bugi" });
        map.put(UnicodeScript.BUHID, new String[] { "buhd" });
        // Byzantine Music: byzm
        map.put(UnicodeScript.CANADIAN_ABORIGINAL, new String[] { "cans" });
        map.put(UnicodeScript.CARIAN, new String[] { "cari" });
        // Caucasian Albanian: aghb
        // Chakma: cakm
        map.put(UnicodeScript.CHAM, new String[] { "cham" });
        map.put(UnicodeScript.CHEROKEE, new String[] { "cher" });
        map.put(UnicodeScript.COMMON, new String[] { SCRIPT_TAG_DEFAULT }); // "Default" in OpenType
        map.put(UnicodeScript.COPTIC, new String[] { "copt" });
        map.put(UnicodeScript.CUNEIFORM, new String[] { "xsux" }); // "Sumero-Akkadian Cuneiform" in OpenType
        map.put(UnicodeScript.CYPRIOT, new String[] { "cprt" });
        map.put(UnicodeScript.CYRILLIC, new String[] { "cyrl" });
        map.put(UnicodeScript.DESERET, new String[] { "dsrt" });
        map.put(UnicodeScript.DEVANAGARI, new String[] { "dev2", "deva" });
        // Duployan: dupl
        map.put(UnicodeScript.EGYPTIAN_HIEROGLYPHS, new String[] { "egyp" });
        // Elbasan: elba
        map.put(UnicodeScript.ETHIOPIC, new String[] { "ethi" });
        map.put(UnicodeScript.GEORGIAN, new String[] { "geor" });
        map.put(UnicodeScript.GLAGOLITIC, new String[] { "glag" });
        map.put(UnicodeScript.GOTHIC, new String[] { "goth" });
        // Grantha: gran
        map.put(UnicodeScript.GREEK, new String[] { "grek" });
        map.put(UnicodeScript.GUJARATI, new String[] { "gjr2", "gujr" });
        map.put(UnicodeScript.GURMUKHI, new String[] { "gur2", "guru" });
        map.put(UnicodeScript.HAN, new String[] { "hani" }); // "CJK Ideographic" in OpenType
        map.put(UnicodeScript.HANGUL, new String[] { "hang" });
        // Hangul Jamo: jamo
        map.put(UnicodeScript.HANUNOO, new String[] { "hano" });
        // Hatran: hatr
        map.put(UnicodeScript.HEBREW, new String[] { "hebr" });
        map.put(UnicodeScript.HIRAGANA, new String[] { "kana" });
        map.put(UnicodeScript.IMPERIAL_ARAMAIC, new String[] { "armi" });
        map.put(UnicodeScript.INHERITED, new String[] { SCRIPT_TAG_INHERITED });
        map.put(UnicodeScript.INSCRIPTIONAL_PAHLAVI, new String[] { "phli" });
        map.put(UnicodeScript.INSCRIPTIONAL_PARTHIAN, new String[] { "prti" });
        map.put(UnicodeScript.JAVANESE, new String[] { "java" });
        map.put(UnicodeScript.KAITHI, new String[] { "kthi" });
        map.put(UnicodeScript.KANNADA, new String[] { "knd2", "knda" });
        map.put(UnicodeScript.KATAKANA, new String[] { "kana" });
        map.put(UnicodeScript.KAYAH_LI, new String[] { "kali" });
        map.put(UnicodeScript.KHAROSHTHI, new String[] { "khar" });
        map.put(UnicodeScript.KHMER, new String[] { "khmr" });
        // Khojki: khoj
        // Khudawadi: sind
        map.put(UnicodeScript.LAO, new String[] { "lao" });
        map.put(UnicodeScript.LATIN, new String[] { "latn" });
        map.put(UnicodeScript.LEPCHA, new String[] { "lepc" });
        map.put(UnicodeScript.LIMBU, new String[] { "limb" });
        // Linear A: lina
        map.put(UnicodeScript.LINEAR_B, new String[] { "linb" });
        map.put(UnicodeScript.LISU, new String[] { "lisu" });
        map.put(UnicodeScript.LYCIAN, new String[] { "lyci" });
        map.put(UnicodeScript.LYDIAN, new String[] { "lydi" });
        // Mahajani: mahj
        map.put(UnicodeScript.MALAYALAM, new String[] { "mlm2", "mlym" });
        map.put(UnicodeScript.MANDAIC, new String[] { "mand" });
        // Manichaean: mani
        // Marchen: marc
        // Mathematical Alphanumeric Symbols: math
        map.put(UnicodeScript.MEETEI_MAYEK, new String[] { "mtei" });
        // Mende Kikakui: mend
        // Meroitic Cursive: merc
        // Meroitic Hieroglyphs: mero
        // Miao: plrd
        // Modi: modi
        map.put(UnicodeScript.MONGOLIAN, new String[] { "mong" });
        // Mro: mroo
        // Multani: mult
        // Musical Symbols: musc
        map.put(UnicodeScript.MYANMAR, new String[] { "mym2", "mymr" });
        // Nabataean: nbat
        // Newa: newa
        map.put(UnicodeScript.NEW_TAI_LUE, new String[] { "talu" });
        map.put(UnicodeScript.NKO, new String[] { "nko " });
        map.put(UnicodeScript.OGHAM, new String[] { "ogam" });
        map.put(UnicodeScript.OL_CHIKI, new String[] { "olck" });
        map.put(UnicodeScript.OLD_ITALIC, new String[] { "ital" });
        // Old Hungarian: hung
        // Old North Arabian: narb
        // Old Permic: perm
        map.put(UnicodeScript.OLD_PERSIAN, new String[] { "xpeo" });
        map.put(UnicodeScript.OLD_SOUTH_ARABIAN, new String[] { "sarb" });
        map.put(UnicodeScript.OLD_TURKIC, new String[] { "orkh" });
        map.put(UnicodeScript.ORIYA, new String[] { "ory2", "orya" }); // "Odia (formerly Oriya)" in OpenType
        // Osage: osge
        map.put(UnicodeScript.OSMANYA, new String[] { "osma" });
        // Pahawh Hmong: hmng
        // Palmyrene: palm
        // Pau Cin Hau: pauc
        map.put(UnicodeScript.PHAGS_PA, new String[] { "phag" });
        map.put(UnicodeScript.PHOENICIAN, new String[] { "phnx" });
        // Psalter Pahlavi: phlp
        map.put(UnicodeScript.REJANG, new String[] { "rjng" });
        map.put(UnicodeScript.RUNIC, new String[] { "runr" });
        map.put(UnicodeScript.SAMARITAN, new String[] { "samr" });
        map.put(UnicodeScript.SAURASHTRA, new String[] { "saur" });
        // Sharada: shrd
        map.put(UnicodeScript.SHAVIAN, new String[] { "shaw" });
        // Siddham: sidd
        // Sign Writing: sgnw
        map.put(UnicodeScript.SINHALA, new String[] { "sinh" });
        // Sora Sompeng: sora
        map.put(UnicodeScript.SUNDANESE, new String[] { "sund" });
        map.put(UnicodeScript.SYLOTI_NAGRI, new String[] { "sylo" });
        map.put(UnicodeScript.SYRIAC, new String[] { "syrc" });
        map.put(UnicodeScript.TAGALOG, new String[] { "tglg" });
        map.put(UnicodeScript.TAGBANWA, new String[] { "tagb" });
        map.put(UnicodeScript.TAI_LE, new String[] { "tale" });
        map.put(UnicodeScript.TAI_THAM, new String[] { "lana" });
        map.put(UnicodeScript.TAI_VIET, new String[] { "tavt" });
        // Takri: takr
        map.put(UnicodeScript.TAMIL, new String[] { "tml2", "taml" });
        // Tangut: tang
        map.put(UnicodeScript.TELUGU, new String[] { "tel2", "telu" });
        map.put(UnicodeScript.THAANA, new String[] { "thaa" });
        map.put(UnicodeScript.THAI, new String[] { "thai" });
        map.put(UnicodeScript.TIBETAN, new String[] { "tibt" });
        map.put(UnicodeScript.TIFINAGH, new String[] { "tfng" });
        // Tirhuta: tirh
        map.put(UnicodeScript.UGARITIC, new String[] { "ugar" });
        map.put(UnicodeScript.UNKNOWN, new String[] { SCRIPT_TAG_DEFAULT });
        map.put(UnicodeScript.VAI, new String[] { "vai " });
        // Warang Citi: wara
        map.put(UnicodeScript.YI, new String[] { "yi  " });
        SCRIPT_TO_TAGS = map;
    }

    /**
     * Convert a {@code UnicodeScript} to one or more OpenType script tags.
     *
     * @param script
     * @return An array of four-char script tags
     * @see #SCRIPT_TO_TAGS
     */
    private static String[] scriptToTags(UnicodeScript script)
    {
        String[] tags = SCRIPT_TO_TAGS.get(script);
        if (tags == null)
        {
            tags = SCRIPT_TO_TAGS.get(UnicodeScript.COMMON);
        }
        return tags;
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
