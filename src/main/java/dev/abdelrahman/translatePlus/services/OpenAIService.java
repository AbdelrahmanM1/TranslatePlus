package dev.abdelrahman.translatePlus.utils;

import org.bukkit.ChatColor;
import java.util.regex.Pattern;

/**
 * Utility class for chat-related operations and formatting
 */
public class ChatUtil {

    // Pattern for detecting color codes
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");

    // Pattern for validating meaningful text (contains at least one alphanumeric character)
    private static final Pattern MEANINGFUL_TEXT_PATTERN = Pattern.compile(".*[a-zA-Z0-9].*");

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

    // Validates Placeholders
    public static String validatePlaceholders(String message, String player, String lang, String status, String service) {
        if (message == null) return "";

        return message.replace("%player%", player != null ? player : "")
                .replace("%lang%", lang != null ? lang : "")
                .replace("%status%", status != null ? status : "")
                .replace("%service%", service != null ? service : "");
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
