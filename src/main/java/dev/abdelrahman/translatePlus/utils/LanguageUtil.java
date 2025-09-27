package dev.abdelrahman.translatePlus.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for language-related operations
 */
public class LanguageUtil {

    // Pattern for valid ISO 639-1 language codes
    private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    // Common English words for language detection
    private static final List<String> COMMON_ENGLISH_WORDS = Arrays.asList(
            "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "a", "an", "is", "are", "was", "were", "be", "been", "have", "has", "had",
            "do", "does", "did", "will", "would", "could", "should", "may", "might",
            "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they",
            "hello", "hi", "yes", "no", "please", "thank", "thanks", "sorry", "ok", "okay"
    );

    /**
     * Validates if a language code follows ISO 639-1 format
     */
    public static boolean isValidLanguageCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return LANGUAGE_CODE_PATTERN.matcher(code.toLowerCase()).matches();
    }

    /**
     * Normalizes a language code or language name to standard format
     */
    public static String normalizeLanguageCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "en"; // Default to English
        }

        String normalized = code.toLowerCase().trim();

        // Check if it's already a valid language code
        if (isValidLanguageCode(normalized)) {
            return normalized;
        }

        // Try to map language names to codes
        Map<String, String> languageMap = getLanguageNameToCodeMapping();
        return languageMap.getOrDefault(normalized, normalized);
    }

    /**
     * Maps common language names to ISO 639-1 codes
     */
    private static Map<String, String> getLanguageNameToCodeMapping() {
        Map<String, String> mapping = new HashMap<>();

        // Major languages
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
        mapping.put("catalan", "ca");
        mapping.put("basque", "eu");
        mapping.put("galician", "gl");
        mapping.put("welsh", "cy");
        mapping.put("irish", "ga");
        mapping.put("maltese", "mt");
        mapping.put("icelandic", "is");

        return mapping;
    }

    /**
     * Gets the display name for a language code
     */
    public static String getLanguageName(String code, Map<String, String> supportedLanguages) {
        if (code == null || code.trim().isEmpty()) {
            return "Unknown";
        }

        String normalizedCode = code.toLowerCase().trim();

        if (supportedLanguages != null && supportedLanguages.containsKey(normalizedCode)) {
            return supportedLanguages.get(normalizedCode);
        }

        // Fallback to hardcoded names
        return getHardcodedLanguageName(normalizedCode);
    }

    /**
     * Hardcoded language names as fallback
     */
    private static String getHardcodedLanguageName(String code) {
        Map<String, String> names = new HashMap<>();
        names.put("en", "English");
        names.put("es", "Spanish");
        names.put("fr", "French");
        names.put("de", "German");
        names.put("it", "Italian");
        names.put("pt", "Portuguese");
        names.put("ru", "Russian");
        names.put("ja", "Japanese");
        names.put("ko", "Korean");
        names.put("zh", "Chinese");
        names.put("ar", "Arabic");
        names.put("hi", "Hindi");
        names.put("tr", "Turkish");
        names.put("pl", "Polish");
        names.put("nl", "Dutch");
        names.put("sv", "Swedish");
        names.put("da", "Danish");
        names.put("no", "Norwegian");
        names.put("fi", "Finnish");
        names.put("he", "Hebrew");
        names.put("th", "Thai");
        names.put("vi", "Vietnamese");
        names.put("id", "Indonesian");
        names.put("ms", "Malay");
        names.put("tl", "Filipino");
        names.put("uk", "Ukrainian");
        names.put("cs", "Czech");
        names.put("hu", "Hungarian");
        names.put("ro", "Romanian");
        names.put("bg", "Bulgarian");
        names.put("hr", "Croatian");
        names.put("sk", "Slovak");
        names.put("sl", "Slovenian");
        names.put("et", "Estonian");
        names.put("lv", "Latvian");
        names.put("lt", "Lithuanian");
        names.put("ca", "Catalan");
        names.put("eu", "Basque");
        names.put("gl", "Galician");
        names.put("cy", "Welsh");
        names.put("ga", "Irish");
        names.put("mt", "Maltese");
        names.put("is", "Icelandic");

        return names.getOrDefault(code, code.toUpperCase());
    }

    /**
     * Attempts to detect if text appears to be in English
     * This is a heuristic approach and not 100% accurate
     */
    public static boolean appearsToBeEnglish(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true; // Empty text is considered "English" to avoid translation
        }

        String lowerText = text.toLowerCase().trim();

        // Remove common Minecraft/server terms that might appear in any language
        String cleanedText = lowerText
                .replaceAll("\\b(minecraft|bukkit|spigot|paper|plugin)\\b", "")
                .trim();

        if (cleanedText.isEmpty()) {
            return true; // Only contained server terms
        }

        // Count English words
        int englishWordCount = 0;
        int totalWords = cleanedText.split("\\s+").length;

        for (String englishWord : COMMON_ENGLISH_WORDS) {
            if (containsWord(cleanedText, englishWord)) {
                englishWordCount++;
            }
        }

        // Check for English patterns
        boolean hasEnglishPattern = hasEnglishCharacteristics(cleanedText);

        // Consider it English if:
        // 1. At least 30% of words are common English words, OR
        // 2. It has English characteristics and at least one English word
        double englishRatio = totalWords > 0 ? (double) englishWordCount / totalWords : 0;

        return englishRatio >= 0.3 || (hasEnglishPattern && englishWordCount > 0);
    }

    /**
     * Checks if text contains a specific word (whole word matching)
     */
    private static boolean containsWord(String text, String word) {
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(pattern).matcher(text).find();
    }

    /**
     * Checks for English language characteristics
     */
    private static boolean hasEnglishCharacteristics(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // English typically uses:
        // - Articles (a, an, the)
        // - Common verb forms (is, are, was, were)
        // - Common prepositions (in, on, at, to, for)
        String[] englishIndicators = {"the ", " is ", " are ", " and ", " to ", " of ", " in ", " on ", " at "};

        for (String indicator : englishIndicators) {
            if (text.contains(indicator)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validates language code format and checks if it's potentially supported
     */
    public static boolean isPotentiallyValidLanguage(String code) {
        if (!isValidLanguageCode(code)) {
            return false;
        }

        // Additional check: ensure it's not just random letters
        Map<String, String> knownCodes = getLanguageNameToCodeMapping();
        return knownCodes.containsValue(code.toLowerCase()) ||
                getHardcodedLanguageName(code.toLowerCase()) != null;
    }

    /**
     * Gets a list of commonly used language codes
     */
    public static List<String> getCommonLanguageCodes() {
        return Arrays.asList(
                "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh",
                "ar", "hi", "tr", "pl", "nl", "sv", "da", "no", "fi", "he"
        );
    }
}