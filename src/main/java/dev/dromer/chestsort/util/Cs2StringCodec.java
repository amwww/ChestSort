package dev.dromer.chestsort.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;

/**
 * Compact encoder/decoder for sharing filters/presets via chat.
 *
 * Format (new):
 * - cs2|filter|<token>,<token>,...
 * - cs2|preset/<name>|<token>,<token>,...
 *
 * Tokens:
 * - Item: oak_log (implies minecraft:oak_log) or mod:item
 * - Tag:  #logs (implies #minecraft:logs) or #c:natural_logs
 * - Tag with exceptions: #logs{dirt,gravel,#c:stone}
 * - Applied preset: @My%20Preset
 * - Autosort enabled: +a
 */
public final class Cs2StringCodec {
    private Cs2StringCodec() {
    }

    /** Preferred prefix for compact cs2 strings. */
    public static final String PREFIX = "cs2|";

    /** Legacy prefix (still accepted by the decoder). */
    public static final String LEGACY_PREFIX = "cs2:";

    private static final char HEADER_SEP = '|';
    private static final char LEGACY_HEADER_SEP = ':';
    /** Separator between whitelist and blacklist token bodies within a preset export/import string. */
    private static final char PRESET_SECTION_SEP = '~';

    public static String encodeFilter(ContainerFilterSpec spec) {
        return PREFIX + "filter" + HEADER_SEP + encodeTokens(spec, Map.of());
    }

    /**
     * Encodes a filter and optionally inlines preset definitions.
     *
     * For each preset name in spec.presets(), if present in inlinePresets, emits `&name{...}`.
     */
    public static String encodeFilter(ContainerFilterSpec spec, Map<String, ContainerFilterSpec> inlinePresets) {
        Map<String, ContainerFilterSpec> defs = inlinePresets == null ? Map.of() : inlinePresets;
        return PREFIX + "filter" + HEADER_SEP + encodeTokens(spec, defs);
    }

    public static String encodePreset(String presetName, ContainerFilterSpec spec) {
        return encodePreset(presetName, spec, new ContainerFilterSpec(List.of(), List.of(), List.of(), false));
    }

    public static String encodePreset(String presetName, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist) {
        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) {
            return PREFIX + "preset" + HEADER_SEP + encodePresetSections(whitelist, blacklist);
        }
        return PREFIX + "preset/" + escape(name) + HEADER_SEP + encodePresetSections(whitelist, blacklist);
    }

    private static String encodePresetSections(ContainerFilterSpec whitelist, ContainerFilterSpec blacklist) {
        String wl = encodeTokens(whitelist, Map.of());
        String bl = encodeTokens(blacklist, Map.of());
        if (bl == null || bl.isEmpty()) {
            return wl;
        }
        // Only include the blacklist section if it is non-empty.
        return (wl == null ? "" : wl) + PRESET_SECTION_SEP + bl;
    }

    /**
     * Encodes multiple presets into one shareable cs2 string.
     *
     * Format:
     * - cs2|presetList|preset/<name>|<tokens>||preset/<name>|<tokens>...
     *
     * Notes:
        * - Uses "||" between preset entries to avoid ambiguity with header separators.
     */
    public static String encodePresetList(Map<String, ContainerFilterSpec> presets) {
        return encodePresetList(presets, Map.of());
    }

    public static String encodePresetList(Map<String, ContainerFilterSpec> whitelists, Map<String, ContainerFilterSpec> blacklists) {
        if ((whitelists == null || whitelists.isEmpty()) && (blacklists == null || blacklists.isEmpty())) {
            return PREFIX + "presetList" + HEADER_SEP;
        }

        ArrayList<String> entries = new ArrayList<>();
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (whitelists != null) names.addAll(whitelists.keySet());
        if (blacklists != null) {
            for (String k : blacklists.keySet()) {
                if (k != null && !names.contains(k)) names.add(k);
            }
        }

        for (String rawName : names) {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty()) continue;

            ContainerFilterSpec wl = whitelists == null ? null : whitelists.get(name);
            ContainerFilterSpec bl = blacklists == null ? null : blacklists.get(name);
            if ((wl == null || wl.isEmpty()) && (bl == null || bl.isEmpty())) continue;

            entries.add("preset/" + escape(name) + HEADER_SEP + encodePresetSections(wl, bl));
        }

        return PREFIX + "presetList" + HEADER_SEP + String.join("||", entries);
    }

    public record DecodedPresetList(Map<String, ContainerFilterSpec> whitelists, Map<String, ContainerFilterSpec> blacklists) {
    }

    public record DecodedPresetImport(String name, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist) {
    }

    /** Back-compat accessor for older callers that only care about whitelists. */
    public static Map<String, ContainerFilterSpec> decodePresetListWhitelists(String raw) {
        return decodePresetList(raw).whitelists();
    }

    /** Back-compat accessor for older callers that only care about a single whitelist spec. */
    public static ContainerFilterSpec decodePresetImportWhitelist(String raw) {
        return decodePresetImport(raw).whitelist();
    }

    /**
     * Decodes a preset import string.
     *
     * Requires the preset to have a name (i.e. `cs2|preset/<name>|...`).
        * Does not accept presetList; use {@link #decodePresetList(String)} for that.
     */
    public static DecodedPresetImport decodePresetImport(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty");
        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty");

        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.replaceAll("\\s+", "");

        s = stripAnyPrefix(s);

        if (startsWithHeader(s, "presetList")) {
            throw new IllegalArgumentException("use presetList import");
        }

        if (startsWithHeader(s, "preset")) {
            // `preset|...` has no name; reject.
            if (!s.startsWith("preset/")) {
                throw new IllegalArgumentException("missing preset name");
            }
        }

        if (!s.startsWith("preset/")) {
            throw new IllegalArgumentException("not a preset");
        }

        int sep = indexOfHeaderBodySeparator(s);
        if (sep < 0) throw new IllegalArgumentException("invalid preset header");

        String header = s.substring(0, sep);
        String nameEsc = header.substring("preset/".length());
        String name = unescape(nameEsc == null ? "" : nameEsc).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("empty preset name");

        String body = s.substring(sep + 1);
        String[] parts = splitTopLevelOnFirst(body, PRESET_SECTION_SEP);
        ContainerFilterSpec wl = decodeCompact("preset" + HEADER_SEP + (parts[0] == null ? "" : parts[0])).normalized();
        ContainerFilterSpec bl = decodeCompact("preset" + HEADER_SEP + (parts[1] == null ? "" : parts[1])).normalized();
        return new DecodedPresetImport(name, wl, bl);
    }

    /**
     * Decodes a preset list string.
     *
     * Accepts both:
     * - Legacy: cs2:presetList:preset/foo:<tokens>|preset/bar:<tokens>
     * - Modern:  cs2|presetList|preset/foo|<tokens>||preset/bar|<tokens>
     */
    public static DecodedPresetList decodePresetList(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty");
        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty");

        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.replaceAll("\\s+", "");

        boolean legacy = s.startsWith(LEGACY_PREFIX);
        s = stripAnyPrefix(s);

        if (!startsWithHeader(s, "presetList")) {
            throw new IllegalArgumentException("not presetList");
        }

        // Header separator in the compact string tells us what delimiter scheme to use.
        // Modern strings use '|' header separator and "||" between entries.
        boolean legacyHeader = s.startsWith("presetList" + LEGACY_HEADER_SEP);
        String body = stripHeader(s, "presetList");

        DecodedPresetListBodyV2 decoded = decodePresetListBodyV2(body, legacy || legacyHeader);
        return new DecodedPresetList(decoded.whitelists(), decoded.blacklists());
    }

    public record DecodedFilterImport(ContainerFilterSpec filter, Map<String, ContainerFilterSpec> embeddedPresets) {
    }

    /** Decode a compact cs2 string (filter/preset/presetList). */
    public static ContainerFilterSpec decodeSpec(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty");
        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty");

        // Allow quoted strings (copy/paste from JSON/chat logs).
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }

        // Allow pasted strings containing spaces/newlines.
        s = s.replaceAll("\\s+", "");

        s = stripAnyPrefix(s);

        // New compact format is texty and starts with a known header.
        if (startsWithHeader(s, "filter") || s.startsWith("preset/") || startsWithHeader(s, "preset") || startsWithHeader(s, "presetList")) {
            // Compact format
            if (startsWithHeader(s, "filter")) {
                return decodeFilterImport(raw).filter().normalized();
            }
            return decodeCompact(s).normalized();
        }

        throw new IllegalArgumentException("unknown cs2 format");
    }

    /**
     * Decodes a cs2 string intended for filter import.
     *
     * Supports embedded preset definitions via `&name{...}`.
     */
    public static DecodedFilterImport decodeFilterImport(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty");
        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty");
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.replaceAll("\\s+", "");
        s = stripAnyPrefix(s);

        if (!startsWithHeader(s, "filter")) {
            // No embedded preset support needed; fall back.
            return new DecodedFilterImport(decodeSpec(raw), Map.of());
        }

        return decodeCompactFilter(s);
    }

    private static ContainerFilterSpec decodeCompact(String s) {
        String body = s;
        // Allow multiple presets in a list (mainly for sharing multiple presets at once).
        // Legacy: cs2:presetList:preset/foo:.....|preset/bar:....
        // Modern: cs2|presetList|preset/foo|.....||preset/bar|....
        if (startsWithHeader(body, "filter")) {
            body = stripHeader(body, "filter");
        } else if (body.startsWith("preset/")) {
            int sep = indexOfHeaderBodySeparator(body);
            if (sep < 0) throw new IllegalArgumentException("invalid preset header");
            body = body.substring(sep + 1);
        } else if (startsWithHeader(body, "presetList")) {
            boolean legacyHeader = body.startsWith("presetList" + LEGACY_HEADER_SEP);
            String listBody = stripHeader(body, "presetList");
            DecodedPresetListBodyV2 decoded = decodePresetListBodyV2(listBody, legacyHeader);
            if (decoded.whitelists().isEmpty()) {
                return new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
            }
            // decodeSpec() expects a single spec; for presetList pick the first.
            return decoded.whitelists().values().iterator().next();
        } else if (startsWithHeader(body, "preset")) {
            body = stripHeader(body, "preset");
        } else {
            throw new IllegalArgumentException("unknown cs2 header");
        }

        List<String> items = new ArrayList<>();
        List<TagFilterSpec> tags = new ArrayList<>();
        List<String> presets = new ArrayList<>();
        boolean autosort = false;

        for (String tokRaw : splitTopLevel(body)) {
            if (tokRaw == null) continue;
            String tok = unescape(tokRaw.trim());
            if (tok.isEmpty()) continue;

            if (tok.equals("+a")) {
                autosort = true;
                continue;
            }

            if (tok.startsWith("@") || tok.startsWith("&")) {
                String pn = tok.substring(1);
                if (!pn.isEmpty()) presets.add(pn);
                continue;
            }

            if (tok.startsWith("#")) {
                String tagPart = tok;
                String exceptPart = "";
                int brace = tok.indexOf('{');
                if (brace >= 0 && tok.endsWith("}")) {
                    tagPart = tok.substring(0, brace);
                    exceptPart = tok.substring(brace + 1, tok.length() - 1);
                }

                String tagId = expandTagId(tagPart);
                if (tagId.isEmpty()) continue;

                List<String> exceptions = new ArrayList<>();
                if (!exceptPart.isEmpty()) {
                    for (String excRaw : splitTopLevel(exceptPart)) {
                        if (excRaw == null) continue;
                        String exc = unescape(excRaw.trim());
                        if (exc.isEmpty()) continue;
                        if (exc.startsWith("#")) {
                            exceptions.add(expandTagId(exc));
                        } else {
                            exceptions.add(expandItemId(exc));
                        }
                    }
                }

                tags.add(new TagFilterSpec(tagId, exceptions));
                continue;
            }

            // Item id
            items.add(expandItemId(tok));
        }

        return new ContainerFilterSpec(items, tags, presets, autosort);
    }

    private record DecodedPresetListBodyV2(LinkedHashMap<String, ContainerFilterSpec> whitelists, LinkedHashMap<String, ContainerFilterSpec> blacklists) {
    }

    private static DecodedPresetListBodyV2 decodePresetListBodyV2(String body, boolean legacy) {
        LinkedHashMap<String, ContainerFilterSpec> wlOut = new LinkedHashMap<>();
        LinkedHashMap<String, ContainerFilterSpec> blOut = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) return new DecodedPresetListBodyV2(wlOut, blOut);

        List<String> entries = legacy
            ? splitTopLevelOn(body, '|')
            : splitTopLevelOnDoublePipe(body);

        for (String entryRaw : entries) {
            if (entryRaw == null) continue;
            String entry = entryRaw.trim();
            if (entry.isEmpty()) continue;
            if (!entry.startsWith("preset/")) continue;

            int sep = indexOfHeaderBodySeparator(entry);
            if (sep < 0) continue;

            String nameEnc = entry.substring("preset/".length(), sep);
            String name = unescape(nameEnc).trim();
            if (name.isEmpty()) continue;

            String sections = entry.substring(sep + 1);
            String[] parts = splitTopLevelOnFirst(sections, PRESET_SECTION_SEP);

            try {
                ContainerFilterSpec w = decodeCompact("preset" + HEADER_SEP + (parts[0] == null ? "" : parts[0])).normalized();
                ContainerFilterSpec b = decodeCompact("preset" + HEADER_SEP + (parts[1] == null ? "" : parts[1])).normalized();

                // Ensure the name exists in wlOut so the whitelist map is the source of truth for names.
                wlOut.putIfAbsent(name, new ContainerFilterSpec(List.of(), List.of(), List.of(), false));
                if (w != null && !w.isEmpty()) wlOut.put(name, w);
                if (b != null && !b.isEmpty()) blOut.put(name, b);
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }

        return new DecodedPresetListBodyV2(wlOut, blOut);
    }

    private static DecodedFilterImport decodeCompactFilter(String s) {
        String body = stripHeader(s, "filter");

        List<String> items = new ArrayList<>();
        List<TagFilterSpec> tags = new ArrayList<>();
        List<String> presets = new ArrayList<>();
        boolean autosort = false;

        LinkedHashMap<String, ContainerFilterSpec> embedded = new LinkedHashMap<>();

        for (String tokRaw : splitTopLevel(body)) {
            if (tokRaw == null) continue;
            String tok = unescape(tokRaw.trim());
            if (tok.isEmpty()) continue;

            if (tok.equals("+a")) {
                autosort = true;
                continue;
            }

            // Preset reference or embedded preset definition.
            if (tok.startsWith("&") || tok.startsWith("@")) {
                String rest = tok.substring(1);
                if (rest.isEmpty()) continue;

                String name = rest;
                String inner = "";
                int brace = rest.indexOf('{');
                if (brace >= 0 && rest.endsWith("}")) {
                    name = rest.substring(0, brace);
                    inner = rest.substring(brace + 1, rest.length() - 1);
                }

                name = name.trim();
                if (!name.isEmpty()) {
                    presets.add(name);
                    if (!inner.isEmpty()) {
                        ContainerFilterSpec presetSpec = decodeInlinePresetTokens(inner);
                        embedded.put(name, presetSpec.normalized());
                    }
                }
                continue;
            }

            if (tok.startsWith("#")) {
                // Tag token
                String tagPart = tok;
                String exceptPart = "";
                int brace = tok.indexOf('{');
                if (brace >= 0 && tok.endsWith("}")) {
                    tagPart = tok.substring(0, brace);
                    exceptPart = tok.substring(brace + 1, tok.length() - 1);
                }

                String tagId = expandTagId(tagPart);
                if (tagId.isEmpty()) continue;

                List<String> exceptions = new ArrayList<>();
                if (!exceptPart.isEmpty()) {
                    for (String excRaw : splitTopLevel(exceptPart)) {
                        if (excRaw == null) continue;
                        String exc = unescape(excRaw.trim());
                        if (exc.isEmpty()) continue;
                        if (exc.startsWith("#")) {
                            exceptions.add(expandTagId(exc));
                        } else {
                            exceptions.add(expandItemId(exc));
                        }
                    }
                }

                tags.add(new TagFilterSpec(tagId, exceptions));
                continue;
            }

            items.add(expandItemId(tok));
        }

        return new DecodedFilterImport(new ContainerFilterSpec(items, tags, presets, autosort), embedded);
    }

    private static ContainerFilterSpec decodeInlinePresetTokens(String body) {
        // Inline preset bodies are just a token list; ignore preset refs inside to avoid recursion.
        List<String> items = new ArrayList<>();
        List<TagFilterSpec> tags = new ArrayList<>();

        for (String tokRaw : splitTopLevel(body)) {
            if (tokRaw == null) continue;
            String tok = unescape(tokRaw.trim());
            if (tok.isEmpty()) continue;
            if (tok.equals("+a")) continue;
            if (tok.startsWith("&") || tok.startsWith("@")) continue;

            if (tok.startsWith("#")) {
                String tagPart = tok;
                String exceptPart = "";
                int brace = tok.indexOf('{');
                if (brace >= 0 && tok.endsWith("}")) {
                    tagPart = tok.substring(0, brace);
                    exceptPart = tok.substring(brace + 1, tok.length() - 1);
                }

                String tagId = expandTagId(tagPart);
                if (tagId.isEmpty()) continue;

                List<String> exceptions = new ArrayList<>();
                if (!exceptPart.isEmpty()) {
                    for (String excRaw : splitTopLevel(exceptPart)) {
                        if (excRaw == null) continue;
                        String exc = unescape(excRaw.trim());
                        if (exc.isEmpty()) continue;
                        if (exc.startsWith("#")) {
                            exceptions.add(expandTagId(exc));
                        } else {
                            exceptions.add(expandItemId(exc));
                        }
                    }
                }

                tags.add(new TagFilterSpec(tagId, exceptions));
                continue;
            }

            items.add(expandItemId(tok));
        }

        return new ContainerFilterSpec(items, tags, List.of(), false);
    }

    private static String encodeTokens(ContainerFilterSpec spec, Map<String, ContainerFilterSpec> inlinePresets) {
        ContainerFilterSpec normalized = (spec == null)
            ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false)
            : spec.normalized();

        ArrayList<String> out = new ArrayList<>();

        if (normalized.items() != null) {
            for (String itemId : normalized.items()) {
                if (itemId == null) continue;
                String t = itemId.trim();
                if (t.isEmpty()) continue;
                out.add(shortenItemId(t));
            }
        }

        if (normalized.tags() != null) {
            for (TagFilterSpec tf : normalized.tags()) {
                if (tf == null) continue;
                String tagId = tf.tagId();
                if (tagId == null) continue;
                String base = shortenTagId(tagId.trim());
                if (base.isEmpty()) continue;

                List<String> exc = tf.exceptions() == null ? List.of() : tf.exceptions();
                if (exc.isEmpty()) {
                    out.add(base);
                } else {
                    ArrayList<String> excTokens = new ArrayList<>();
                    for (String e : exc) {
                        if (e == null) continue;
                        String et = e.trim();
                        if (et.isEmpty()) continue;
                        if (et.startsWith("#")) {
                            excTokens.add(shortenTagId(et));
                        } else {
                            excTokens.add(shortenItemId(et));
                        }
                    }
                    out.add(base + "{" + String.join(",", excTokens) + "}");
                }
            }
        }

        if (normalized.presets() != null) {
            for (String presetName : normalized.presets()) {
                if (presetName == null) continue;
                String t = presetName.trim();
                if (t.isEmpty()) continue;

                ContainerFilterSpec def = inlinePresets == null ? null : inlinePresets.get(t);
                if (def != null && !def.isEmpty()) {
                    out.add("&" + escape(t) + "{" + encodePresetBodyTokens(def) + "}");
                } else {
                    out.add("&" + escape(t));
                }
            }
        }

        if (normalized.autosort()) {
            out.add("+a");
        }

        return String.join(",", out);
    }

    private static String encodePresetBodyTokens(ContainerFilterSpec presetSpec) {
        if (presetSpec == null) return "";
        ContainerFilterSpec normalized = presetSpec.normalized();

        ArrayList<String> out = new ArrayList<>();
        if (normalized.items() != null) {
            for (String itemId : normalized.items()) {
                if (itemId == null) continue;
                String t = itemId.trim();
                if (t.isEmpty()) continue;
                out.add(shortenItemId(t));
            }
        }

        if (normalized.tags() != null) {
            for (TagFilterSpec tf : normalized.tags()) {
                if (tf == null) continue;
                String tagId = tf.tagId();
                if (tagId == null) continue;
                String base = shortenTagId(tagId.trim());
                if (base.isEmpty()) continue;

                List<String> exc = tf.exceptions() == null ? List.of() : tf.exceptions();
                if (exc.isEmpty()) {
                    out.add(base);
                } else {
                    ArrayList<String> excTokens = new ArrayList<>();
                    for (String e : exc) {
                        if (e == null) continue;
                        String et = e.trim();
                        if (et.isEmpty()) continue;
                        if (et.startsWith("#")) {
                            excTokens.add(shortenTagId(et));
                        } else {
                            excTokens.add(shortenItemId(et));
                        }
                    }
                    out.add(base + "{" + String.join(",", excTokens) + "}");
                }
            }
        }

        // Intentionally omit nested presets + autosort; presets contribute only items/tags.
        return String.join(",", out);
    }

    private static String expandItemId(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) return "";
        validateIdDoesNotContainPipe(t);
        if (t.contains(":")) return t;
        // default namespace
        return "minecraft:" + t;
    }

    private static String expandTagId(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) return "";
        if (t.charAt(0) != '#') t = "#" + t;
        String id = t.substring(1);
        validateIdDoesNotContainPipe(id);
        if (id.isEmpty()) return "";
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        return "#" + id;
    }

    private static String shortenItemId(String itemId) {
        String t = itemId == null ? "" : itemId.trim();
        if (t.startsWith("minecraft:")) return t.substring("minecraft:".length());
        return t;
    }

    private static String shortenTagId(String tagId) {
        String t = tagId == null ? "" : tagId.trim();
        if (t.isEmpty()) return "";
        if (t.charAt(0) != '#') t = "#" + t;
        String id = t.substring(1);
        if (id.startsWith("minecraft:")) {
            return "#" + id.substring("minecraft:".length());
        }
        return "#" + id;
    }

    private static void validateIdDoesNotContainPipe(String s) {
        if (s != null && s.indexOf('|') >= 0) {
            throw new IllegalArgumentException("invalid id (contains '|')");
        }
    }

    private static boolean startsWithHeader(String s, String header) {
        if (s == null || header == null) return false;
        return s.startsWith(header + LEGACY_HEADER_SEP) || s.startsWith(header + HEADER_SEP);
    }

    private static String stripHeader(String s, String header) {
        if (s == null) return "";
        String legacy = header + LEGACY_HEADER_SEP;
        String modern = header + HEADER_SEP;
        if (s.startsWith(legacy)) return s.substring(legacy.length());
        if (s.startsWith(modern)) return s.substring(modern.length());
        throw new IllegalArgumentException("unknown cs2 header");
    }

    private static String stripAnyPrefix(String s) {
        if (s == null) return "";
        if (s.startsWith(PREFIX)) return s.substring(PREFIX.length());
        if (s.startsWith(LEGACY_PREFIX)) return s.substring(LEGACY_PREFIX.length());
        return s;
    }

    private static int indexOfHeaderBodySeparator(String presetHeader) {
        // preset/<escapedName><sep><tokens>
        int pipe = presetHeader.indexOf(HEADER_SEP);
        int colon = presetHeader.indexOf(LEGACY_HEADER_SEP);
        if (pipe < 0) return colon;
        if (colon < 0) return pipe;
        return Math.min(pipe, colon);
    }

    /** Split on the first occurrence of delim at top-level (not inside {...}). Returns [left,right]. */
    private static String[] splitTopLevelOnFirst(String s, char delim) {
        if (s == null || s.isEmpty()) return new String[] { "", "" };
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && depth > 0) depth--;
            if (c == delim && depth == 0) {
                return new String[] { s.substring(0, i), (i + 1 < s.length()) ? s.substring(i + 1) : "" };
            }
        }
        return new String[] { s, "" };
    }

    /** Split comma-separated tokens, but do not split inside {...}. */
    private static List<String> splitTopLevel(String s) {
        if (s == null || s.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && depth > 0) depth--;

            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private static List<String> splitTopLevelOn(String s, char delim) {
        if (s == null || s.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && depth > 0) depth--;

            if (c == delim && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    /** Split on "||" at top-level (not inside {...}). */
    private static List<String> splitTopLevelOnDoublePipe(String s) {
        if (s == null || s.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && depth > 0) depth--;

            if (c == '|' && depth == 0 && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                out.add(cur.toString());
                cur.setLength(0);
                i++; // consume second '|'
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isSafe(c)) {
                out.append(c);
            } else {
                out.append('%');
                out.append(toHex((c >> 4) & 0xF));
                out.append(toHex(c & 0xF));
            }
        }
        return out.toString();
    }

    private static String unescape(String s) {
        if (s == null || s.indexOf('%') < 0) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = fromHex(s.charAt(i + 1));
                int lo = fromHex(s.charAt(i + 2));
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) | lo));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isSafe(char c) {
        return (c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9')
            || c == '_' || c == '-' || c == '.';
    }

    private static char toHex(int v) {
        return (char) (v < 10 ? ('0' + v) : ('A' + (v - 10)));
    }

    private static int fromHex(char c) {
        if (c >= '0' && c <= '9') return (c - '0');
        if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
        if (c >= 'a' && c <= 'f') return (c - 'a' + 10);
        return -1;
    }
}
