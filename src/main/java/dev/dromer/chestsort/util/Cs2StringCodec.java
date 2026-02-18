package dev.dromer.chestsort.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;

/**
 * Compact encoder/decoder for sharing filters/presets via chat.
 *
 * Format (new):
 * - cs2:filter:<token>,<token>,...
 * - cs2:preset/<name>:<token>,<token>,...
 *
 * Tokens:
 * - Item: oak_log (implies minecraft:oak_log) or mod:item
 * - Tag:  #logs (implies #minecraft:logs) or #c:stones
 * - Tag with exceptions: #logs{dirt,gravel,#c:stone}
 * - Applied preset: @My%20Preset
 * - Autosort enabled: +a
 *
 * Decoder remains backward-compatible with legacy cs2:<base64url(json)> strings.
 */
public final class Cs2StringCodec {
    private Cs2StringCodec() {
    }

    public static final String PREFIX = "cs2:";

    public static String encodeFilter(ContainerFilterSpec spec) {
        return PREFIX + "filter:" + encodeTokens(spec, Map.of());
    }

    /**
     * Encodes a filter and optionally inlines preset definitions.
     *
     * For each preset name in spec.presets(), if present in inlinePresets, emits `&name{...}`.
     */
    public static String encodeFilter(ContainerFilterSpec spec, Map<String, ContainerFilterSpec> inlinePresets) {
        Map<String, ContainerFilterSpec> defs = inlinePresets == null ? Map.of() : inlinePresets;
        return PREFIX + "filter:" + encodeTokens(spec, defs);
    }

    public static String encodePreset(String presetName, ContainerFilterSpec spec) {
        String name = presetName == null ? "" : presetName.trim();
        if (name.isEmpty()) {
            return PREFIX + "preset:" + encodeTokens(spec, Map.of());
        }
        return PREFIX + "preset/" + escape(name) + ":" + encodeTokens(spec, Map.of());
    }

    public record DecodedFilterImport(ContainerFilterSpec filter, Map<String, ContainerFilterSpec> embeddedPresets) {
    }

    /** Decode either a new compact format string or a legacy base64url(json) string. */
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

        if (s.startsWith(PREFIX)) {
            s = s.substring(PREFIX.length());
        }

        // New compact format is texty and starts with a known header.
        if (s.startsWith("filter:") || s.startsWith("preset/") || s.startsWith("preset:")) {
            // Compact format
            if (s.startsWith("filter:")) {
                return decodeFilterImport(raw).filter().normalized();
            }
            return decodeCompact(s).normalized();
        }

        // Legacy: base64url(json)
        return decodeLegacyBase64Json(s).normalized();
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
        if (s.startsWith(PREFIX)) {
            s = s.substring(PREFIX.length());
        }

        if (!s.startsWith("filter:")) {
            // No embedded preset support needed; fall back.
            return new DecodedFilterImport(decodeSpec(raw), Map.of());
        }

        return decodeCompactFilter(s);
    }

    private static ContainerFilterSpec decodeCompact(String s) {
        String body = s;
        if (body.startsWith("filter:")) {
            body = body.substring("filter:".length());
        } else if (body.startsWith("preset/")) {
            int colon = body.indexOf(':');
            if (colon < 0) throw new IllegalArgumentException("invalid preset header");
            body = body.substring(colon + 1);
        } else if (body.startsWith("preset:")) {
            body = body.substring("preset:".length());
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

    private static DecodedFilterImport decodeCompactFilter(String s) {
        String body = s.substring("filter:".length());

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

    private static ContainerFilterSpec decodeLegacyBase64Json(String s) {
        String t = s;
        int mod = t.length() & 3;
        if (mod != 0) {
            t = t + "=".repeat(4 - mod);
        }

        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(t);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not base64url");
        }
        String jsonString = new String(bytes, StandardCharsets.UTF_8);

        JsonElement json;
        try {
            json = JsonParser.parseString(jsonString);
        } catch (Exception e) {
            throw new IllegalArgumentException("not json");
        }

        return ContainerFilterSpec.CODEC.parse(JsonOps.INSTANCE, json)
            .result()
            .orElseThrow(() -> new IllegalArgumentException("failed to decode"));
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
        if (t.contains(":")) return t;
        // default namespace
        return "minecraft:" + t;
    }

    private static String expandTagId(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) return "";
        if (t.charAt(0) != '#') t = "#" + t;
        String id = t.substring(1);
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
