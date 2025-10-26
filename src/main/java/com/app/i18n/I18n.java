package com.app.i18n;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON-based i18n loader for flat key/value files.
 * - No external dependencies
 * - Loads /i18n/en.json as a base and overlays the selected language
 * - Caches per-language instances
 * - Provides simple {0}, {1} formatting
 */
public final class I18n {

    // ---- Public API ---------------------------------------------------------

    /** Supported languages. */
    public enum Language {
        /** English */
        EN("en"),

        /** Portuguese */
        PT("pt");

        private final String code;

        /** Language code. */
        Language(String code) { this.code = code; }

        /** Returns the language code.
         *
         * @return the code
        */
        public String code() { return code; }

        /** Returns the Language for the given code, or EN if unknown.
         * 
         * @param code the language code
         * 
         * @return the Language
        */
        public static Language fromCodeOrDefault(String code) {
            if ("pt".equalsIgnoreCase(code)) return PT;
            return EN;
        }
    }

    /** Get (or load) a cached I18n instance for the given language code.
     * 
     * @param languageCode the language code, e.g. "en" or "pt"
     *
     * @return the I18n instance for the given language
    */
    public static I18n forLanguageCode(String languageCode) {
        return forLanguage(Language.fromCodeOrDefault(languageCode));
    }

    /** Get (or load) a cached I18n instance for the given language.
     * 
     * @param language the language
     *
     * @return the I18n instance for the given language
    */
    public static I18n forLanguage(Language language) {
        return CACHED_BY_LANGUAGE.computeIfAbsent(language, I18n::loadForLanguage);
    }

    /** Returns the raw message for the given key, or !key! if missing.
     * 
     * @param key the message key
     * 
     * @return the message
    */
    public String get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return messagesByKey.getOrDefault(key, "!" + key + "!");
    }

    /** Returns the message with {0}, {1}, ... placeholders replaced by args.
     * 
     * @param key the message key
     * @param arguments the arguments to replace placeholders
     * 
     * @return the formatted message
    */
    public String format(String key, Object... arguments) {
        String template = get(key);
        return applyPlaceholders(template, arguments);
    }

    // ---- Implementation details --------------------------------------------

    private static final Map<Language, I18n> CACHED_BY_LANGUAGE = new ConcurrentHashMap<>();
    private final Map<String, String> messagesByKey;

    private I18n(Map<String, String> messagesByKey) {
        this.messagesByKey = Map.copyOf(messagesByKey); // make it immutable
    }

    /** Loads EN base, then overlays selected language if different from EN. */
    private static I18n loadForLanguage(Language language) {
        Map<String, String> mergedMessages = new HashMap<>();

        // Base messages (English)
        mergedMessages.putAll(readFlatJsonFromClasspath("/i18n/en.json"));

        // Overlay with selected language (if not EN)
        if (language != Language.EN) {
            Map<String, String> overlayMessages =
                    readFlatJsonFromClasspath("/i18n/" + language.code() + ".json");
            mergedMessages.putAll(overlayMessages);
        }

        return new I18n(mergedMessages);
    }

    /**
     * Reads a classpath resource and parses a flat JSON object into a Map.
     * Expected format: { "key": "value", "another": "text" }
     */
    private static Map<String, String> readFlatJsonFromClasspath(String resourcePath) {
        Map<String, String> messages = new HashMap<>();
        try (InputStream inputStream = I18n.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) return messages; // missing resource → empty map
            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            messages.putAll(parseFlatStringObjectJson(jsonContent));
        } catch (Exception ignored) {
            // On parse/read errors, return whatever was collected (likely empty).
        }
        return messages;
    }

    /**
     * Minimal parser for a flat JSON object with string keys/values.
     * Supports: whitespace, \" and \\ escapes, and \n newlines.
     * This is intentionally tiny—replace with a real JSON lib if desired.
     */
    private static Map<String, String> parseFlatStringObjectJson(String jsonContent) {
        Map<String, String> parsed = new HashMap<>();
        if (jsonContent == null) return parsed;

        int index = skipWhitespace(jsonContent, 0);
        if (index >= jsonContent.length() || jsonContent.charAt(index) != '{') return parsed;
        index = skipWhitespace(jsonContent, index + 1);

        while (index < jsonContent.length() && jsonContent.charAt(index) != '}') {
            // parse key
            if (jsonContent.charAt(index) != '"') return parsed;
            StringReadResult key = readQuotedString(jsonContent, index + 1);
            index = skipWhitespace(jsonContent, key.nextIndex());
            if (index >= jsonContent.length() || jsonContent.charAt(index) != ':') return parsed;
            index = skipWhitespace(jsonContent, index + 1);

            // parse value
            if (index >= jsonContent.length() || jsonContent.charAt(index) != '"') return parsed;
            StringReadResult value = readQuotedString(jsonContent, index + 1);
            parsed.put(key.value(), value.value());

            index = skipWhitespace(jsonContent, value.nextIndex());
            if (index < jsonContent.length() && jsonContent.charAt(index) == ',') {
                index = skipWhitespace(jsonContent, index + 1);
            } else {
                break;
            }
        }
        return parsed;
    }

    private static int skipWhitespace(String text, int startIndex) {
        int index = startIndex;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    private static StringReadResult readQuotedString(String text, int startIndex) {
        StringBuilder valueBuilder = new StringBuilder();
        int index = startIndex;

        while (index < text.length()) {
            char current = text.charAt(index++);
            switch (current) {
                case '\\' -> {
                    if (index >= text.length()) break;
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case 'n' -> valueBuilder.append('\n');
                        case 'r' -> valueBuilder.append('\r');
                        case 't' -> valueBuilder.append('\t');
                        case '"' -> valueBuilder.append('"');
                        case '\\' -> valueBuilder.append('\\');
                        default -> valueBuilder.append(escape);
                    }
                }
                case '"' -> {
                    return new StringReadResult(valueBuilder.toString(), index);
                }
                default -> valueBuilder.append(current);
            }
        }
        return new StringReadResult(valueBuilder.toString(), index);
    }

    private static String applyPlaceholders(String template, Object... arguments) {
        if (template == null || arguments == null || arguments.length == 0) return template;
        String result = template;
        for (int i = 0; i < arguments.length; i++) {
            String placeholder = "{" + i + "}";
            result = result.replace(placeholder, String.valueOf(arguments[i]));
        }
        return result;
    }

    // Small immutable result holder for the string reader
    private record StringReadResult(String value, int nextIndex) {}
}
