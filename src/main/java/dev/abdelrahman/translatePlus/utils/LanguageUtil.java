package dev.abdelrahman.translatePlus.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class LanguageUtil {

    private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    public static boolean isValidLanguageCode(String code) {
        return code != null && LANGUAGE_CODE_PATTERN.matcher(code).matches();
    }

    public static String normalizeLanguageCode(String code) {
        if (code == null) {
            return "en";
        }

        String normalized = code.toLowerCase().trim();

        Map<String, String> languageMap = getLanguageCodeMapping();

        return languageMap.getOrDefault(normalized, normalized);
    }

    private static Map<String, String> getLanguageCodeMapping() {
        Map<String, String> mapping = new HashMap<>();

        mapping.put("english", "en");
        mapping.put("spanish", "es");
        mapping.put("french", "fr");
        mapping.put("german", "de");
        mapping.put("italian", "it");
        mapping.put("portuguese", "pt");
        mapping.put("russian", "ru");
        mapping.put("japanese", "ja");
        mapping.put("korean", "ko");
        mapping.put("chinese", "zh");
        mapping.put("arabic", "ar");
        mapping.put("hindi", "hi");
        mapping.put("turkish", "tr");
        mapping.put("polish", "pl");
        mapping.put("dutch", "nl");
        mapping.put("swedish", "sv");
        mapping.put("danish", "da");
        mapping.put("norwegian", "no");
        mapping.put("finnish", "fi");
        mapping.put("hebrew", "he");
        mapping.put("thai", "th");
        mapping.put("vietnamese", "vi");
        mapping.put("indonesian", "id");
        mapping.put("malay", "ms");
        mapping.put("filipino", "tl");
        mapping.put("ukrainian", "uk");
        mapping.put("czech", "cs");
        mapping.put("hungarian", "hu");
        mapping.put("romanian", "ro");
        mapping.put("bulgarian", "bg");
        mapping.put("croatian", "hr");
        mapping.put("slovak", "sk");
        mapping.put("slovenian", "sl");
        mapping.put("estonian", "et");
        mapping.put("latvian", "lv");
        mapping.put("lithuanian", "lt");

        return mapping;
    }

    public static String getLanguageName(String code, Map<String, String> supportedLanguages) {
        if (supportedLanguages == null) {
            return code;
        }

        return supportedLanguages.getOrDefault(code.toLowerCase(), code.toUpperCase());
    }

    public static boolean appearsToBeEnglish(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }

        String lowerText = text.toLowerCase();
        String[] commonEnglishWords = {
                "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
                "a", "an", "is", "are", "was", "were", "be", "been", "have", "has", "had",
                "do", "does", "did", "will", "would", "could", "should", "may", "might",
                "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they"
        };

        int englishWordCount = 0;
        for (String word : commonEnglishWords) {
            if (lowerText.contains(" " + word + " ") ||
                    lowerText.startsWith(word + " ") ||
                    lowerText.endsWith(" " + word)) {
                englishWordCount++;
            }
        }

        return englishWordCount >= 2;
    }
}