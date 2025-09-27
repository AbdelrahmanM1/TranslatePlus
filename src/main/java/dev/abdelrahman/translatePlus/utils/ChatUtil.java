package dev.abdelrahman.translatePlus.utils;

import dev.abdelrahman.translatePlus.managers.TranslateServiceManager;
import org.bukkit.ChatColor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for chat-related operations and formatting
 */
public class ChatUtil {

    // Pattern for detecting color codes
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");

    // Pattern for validating meaningful text (contains at least one alphanumeric character)
    private static final Pattern MEANINGFUL_TEXT_PATTERN = Pattern.compile(".*[a-zA-Z0-9].*");

    // Language code to language name mapping
    private static final Map<String, String> LANGUAGE_NAMES = new HashMap<>();

    static {
        // Initialize the language map
        LANGUAGE_NAMES.put("en", "English");
        LANGUAGE_NAMES.put("es", "Spanish");
        LANGUAGE_NAMES.put("fr", "French");
        LANGUAGE_NAMES.put("de", "German");
        LANGUAGE_NAMES.put("it", "Italian");
        LANGUAGE_NAMES.put("pt", "Portuguese");
        LANGUAGE_NAMES.put("ru", "Russian");
        LANGUAGE_NAMES.put("ja", "Japanese");
        LANGUAGE_NAMES.put("ko", "Korean");
        LANGUAGE_NAMES.put("zh", "Chinese");
        LANGUAGE_NAMES.put("ar", "Arabic");
        LANGUAGE_NAMES.put("hi", "Hindi");
        LANGUAGE_NAMES.put("tr", "Turkish");
        LANGUAGE_NAMES.put("pl", "Polish");
        LANGUAGE_NAMES.put("nl", "Dutch");
        LANGUAGE_NAMES.put("sv", "Swedish");
        LANGUAGE_NAMES.put("da", "Danish");
        LANGUAGE_NAMES.put("no", "Norwegian");
        LANGUAGE_NAMES.put("fi", "Finnish");
        LANGUAGE_NAMES.put("he", "Hebrew");
        LANGUAGE_NAMES.put("th", "Thai");
        LANGUAGE_NAMES.put("vi", "Vietnamese");
        LANGUAGE_NAMES.put("id", "Indonesian");
        LANGUAGE_NAMES.put("ms", "Malay");
        LANGUAGE_NAMES.put("tl", "Filipino");
        LANGUAGE_NAMES.put("uk", "Ukrainian");
        LANGUAGE_NAMES.put("cs", "Czech");
        LANGUAGE_NAMES.put("hu", "Hungarian");
        LANGUAGE_NAMES.put("ro", "Romanian");
        LANGUAGE_NAMES.put("bg", "Bulgarian");
        LANGUAGE_NAMES.put("hr", "Croatian");
        LANGUAGE_NAMES.put("sk", "Slovak");
        LANGUAGE_NAMES.put("sl", "Slovenian");
        LANGUAGE_NAMES.put("et", "Estonian");
        LANGUAGE_NAMES.put("lv", "Latvian");
        LANGUAGE_NAMES.put("lt", "Lithuanian");
        LANGUAGE_NAMES.put("ca", "Catalan");
        LANGUAGE_NAMES.put("eu", "Basque");
        LANGUAGE_NAMES.put("gl", "Galician");
        LANGUAGE_NAMES.put("cy", "Welsh");
        LANGUAGE_NAMES.put("ga", "Irish");
        LANGUAGE_NAMES.put("mt", "Maltese");
        LANGUAGE_NAMES.put("is", "Icelandic");
        LANGUAGE_NAMES.put("sr", "Serbian");
        LANGUAGE_NAMES.put("el", "Greek");
    }

    /**
     * Converts & color codes to Minecraft color codes
     */
    public static String colorize(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Removes all color codes from a message
     */
    public static String stripColors(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.stripColor(message);
    }

    /**
     * Formats a message with prefix and color codes
     */
    public static String formatMessage(String prefix, String message) {
        if (prefix == null) prefix = "";
        if (message == null) message = "";

        return colorize(prefix + message);
    }

    /**
     * Validates and replaces placeholders in messages
     * Enhanced version that takes TranslateServiceManager for better service integration
     */
    public static String validatePlaceholders(String message, String player, String lang, String status, TranslateServiceManager serviceManager) {
        if (message == null) return "";

        String serviceName = (serviceManager != null) ? serviceManager.getServiceDisplayName() : "UNKNOWN";

        return message.replace("%player%", player != null ? player : "")
                .replace("%lang%", lang != null ? lang : "")
                .replace("%status%", status != null ? status : "")
                .replace("%service%", serviceName);
    }

    /**
     * Legacy method for backward compatibility
     * Use the TranslateServiceManager version when possible
     */
    public static String validatePlaceholders(String message, String player, String lang, String status, String service) {
        if (message == null) return "";

        return message.replace("%player%", player != null ? player : "")
                .replace("%lang%", lang != null ? lang : "")
                .replace("%status%", status != null ? status : "")
                .replace("%service%", service != null ? service : "UNKNOWN");
    }

    /**
     * Enhanced placeholder replacement with comprehensive language formatting
     */
    public static String replacePlaceholders(String message, String player, String lang, String status, TranslateServiceManager serviceManager) {
        if (message == null) return "";

        String formattedLang = formatLanguageName(lang);
        String formattedStatus = formatStatus(status);
        String formattedService = (serviceManager != null) ? serviceManager.getServiceDisplayName() : "UNKNOWN";

        return message
                .replace("%player%", player != null ? player : "Unknown")
                .replace("%lang%", formattedLang)
                .replace("%status%", formattedStatus)
                .replace("%service%", formattedService);
    }

    /**
     * Format language name for display (e.g., "es" -> "Spanish (es)")
     * Now uses a static map for better maintainability and performance
     */
    private static String formatLanguageName(String langCode) {
        if (langCode == null) return "Unknown";

        String languageName = LANGUAGE_NAMES.get(langCode.toLowerCase());
        if (languageName != null) {
            return languageName + " (" + langCode + ")";
        }

        // Fallback for unknown language codes
        return langCode.toUpperCase() + " (" + langCode + ")";
    }

    /**
     * Gets the display name for a language code without the code suffix
     * @param langCode the language code (e.g., "en", "es")
     * @return the language name (e.g., "English", "Spanish") or the uppercase code if unknown
     */
    public static String getLanguageName(String langCode) {
        if (langCode == null) return "Unknown";

        String languageName = LANGUAGE_NAMES.get(langCode.toLowerCase());
        return languageName != null ? languageName : langCode.toUpperCase();
    }

    /**
     * Checks if a language code is supported
     * @param langCode the language code to check
     * @return true if the language is supported, false otherwise
     */
    public static boolean isSupportedLanguage(String langCode) {
        return langCode != null && LANGUAGE_NAMES.containsKey(langCode.toLowerCase());
    }

    /**
     * Gets all supported language codes
     * @return a copy of the supported language codes
     */
    public static java.util.Set<String> getSupportedLanguageCodes() {
        return new java.util.HashSet<>(LANGUAGE_NAMES.keySet());
    }

    /**
     * Gets all supported language names
     * @return a copy of the supported language names
     */
    public static java.util.Collection<String> getSupportedLanguageNames() {
        return new java.util.ArrayList<>(LANGUAGE_NAMES.values());
    }

    /**
     * Format status for display
     */
    private static String formatStatus(String status) {
        if (status == null) return "UNKNOWN";
        return status.toUpperCase();
    }

    /**
     * Create a formatted status message using the service manager
     */
    public static String createStatusMessage(String messageTemplate, String playerName, String language, boolean isEnabled, TranslateServiceManager serviceManager) {
        String status = isEnabled ? "ENABLED" : "DISABLED";
        return validatePlaceholders(messageTemplate, playerName, language, status, serviceManager);
    }

    /**
     * Validates if a message is suitable for translation
     */
    public static boolean isValidMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        // Remove color codes for validation
        String cleanMessage = stripColors(message).trim();

        // Check length constraints
        if (cleanMessage.length() < 1 || cleanMessage.length() > 1000) {
            return false;
        }

        // Must contain at least one alphanumeric character
        return MEANINGFUL_TEXT_PATTERN.matcher(cleanMessage).matches();
    }

    /**
     * Validates message length against specific constraints
     */
    public static boolean isValidMessageLength(String message, int minLength, int maxLength) {
        if (message == null) {
            return false;
        }

        String cleanMessage = stripColors(message).trim();
        int length = cleanMessage.length();

        return length >= minLength && length <= maxLength;
    }

    /**
     * Escapes special JSON characters in text
     */
    public static String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Truncates message to specified length with ellipsis
     */
    public static String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }

        if (maxLength <= 3) {
            return message.length() <= maxLength ? message : "...";
        }

        if (message.length() <= maxLength) {
            return message;
        }

        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Formats a translated message with translation indicator
     */
    public static String formatTranslatedMessage(String originalFormat, String senderName,
                                                 String translatedMessage, boolean isRTL) {
        String translationSuffix = isRTL ? " §8[مترجم]" : " §8[Translated]";
        return String.format(originalFormat, senderName, translatedMessage + translationSuffix);
    }

    /**
     * Checks if message contains only special characters (no meaningful content)
     */
    public static boolean containsOnlySpecialCharacters(String message) {
        if (message == null || message.trim().isEmpty()) {
            return true;
        }

        String cleanMessage = stripColors(message).trim();
        return !MEANINGFUL_TEXT_PATTERN.matcher(cleanMessage).matches();
    }

    /**
     * Cleans message for translation by removing colors and normalizing whitespace
     */
    public static String cleanMessageForTranslation(String message) {
        if (message == null) {
            return "";
        }

        // Remove color codes
        String cleaned = stripColors(message);

        // Trim whitespace
        cleaned = cleaned.trim();

        // Normalize multiple spaces to single spaces
        cleaned = cleaned.replaceAll("\\s+", " ");

        // Remove leading/trailing punctuation that might interfere with translation
        cleaned = cleaned.replaceAll("^[\\s\\p{Punct}]+|[\\s\\p{Punct}]+$", "");

        return cleaned;
    }

    /**
     * Checks if text contains color codes
     */
    public static boolean hasColorCodes(String message) {
        if (message == null) {
            return false;
        }
        return COLOR_CODE_PATTERN.matcher(message).find();
    }

    /**
     * Preserves color codes from original message in translated message
     */
    public static String preserveColorCodes(String originalMessage, String translatedMessage) {
        if (originalMessage == null || translatedMessage == null) {
            return translatedMessage != null ? translatedMessage : "";
        }

        // If original has no color codes, return translated as-is
        if (!hasColorCodes(originalMessage)) {
            return translatedMessage;
        }

        // Simple approach: if original message starts with color codes, apply them to translation
        String colorPrefix = extractColorPrefix(originalMessage);
        if (!colorPrefix.isEmpty()) {
            return colorPrefix + stripColors(translatedMessage);
        }

        return translatedMessage;
    }

    /**
     * Extracts color codes from the beginning of a message
     */
    private static String extractColorPrefix(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < message.length() - 1; i++) {
            if (message.charAt(i) == '&') {
                char colorChar = message.charAt(i + 1);
                if (isValidColorCode(colorChar)) {
                    prefix.append('&').append(colorChar);
                    i++; // Skip the color character
                } else {
                    break;
                }
            } else if (message.charAt(i) != ' ') {
                break; // Stop at first non-space, non-color character
            }
        }

        return prefix.toString();
    }

    /**
     * Checks if character is a valid Minecraft color code
     */
    private static boolean isValidColorCode(char c) {
        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(c)) != -1;
    }

    /**
     * Safely formats a chat message with sender name and content
     */
    public static String formatChatMessage(String format, String senderName, String message) {
        if (format == null) {
            format = "<%s> %s";
        }
        if (senderName == null) {
            senderName = "Unknown";
        }
        if (message == null) {
            message = "";
        }

        try {
            return String.format(format, senderName, message);
        } catch (Exception e) {
            // Fallback formatting if the format string is invalid
            return senderName + ": " + message;
        }
    }

    /**
     * Checks if a message appears to be a command
     */
    public static boolean isCommand(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        return message.trim().startsWith("/");
    }

    /**
     * Gets the length of a message without color codes
     */
    public static int getCleanLength(String message) {
        if (message == null) {
            return 0;
        }
        return stripColors(message).length();
    }

    /**
     * Validates that a message is appropriate for public chat
     */
    public static boolean isAppropriateForChat(String message) {
        if (!isValidMessage(message)) {
            return false;
        }

        String clean = cleanMessageForTranslation(message);

        // Additional checks can be added here for inappropriate content
        // For now, just ensure it's not empty after cleaning
        return !clean.trim().isEmpty();
    }
}