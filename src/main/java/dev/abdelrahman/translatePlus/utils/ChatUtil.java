package dev.abdelrahman.translatePlus.utils;

import org.bukkit.ChatColor;

public class ChatUtil {

    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String stripColors(String message) {
        return ChatColor.stripColor(message);
    }

    public static String formatMessage(String prefix, String message) {
        return colorize(prefix + message);
    }

    public static boolean isValidMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String cleanMessage = stripColors(message).trim();

        if (cleanMessage.length() < 1 || cleanMessage.length() > 500) {
            return false;
        }

        return cleanMessage.matches(".*[a-zA-Z0-9].*");
    }

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

    public static String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }

        if (message.length() <= maxLength) {
            return message;
        }

        return message.substring(0, maxLength - 3) + "...";
    }

    public static String formatTranslatedMessage(String originalFormat, String senderName, String translatedMessage) {
        return String.format(originalFormat, senderName, translatedMessage + " §8[Translated]");
    }

    public static boolean containsOnlySpecialCharacters(String message) {
        if (message == null || message.trim().isEmpty()) {
            return true;
        }

        String cleanMessage = stripColors(message).trim();
        return !cleanMessage.matches(".*[a-zA-Z0-9].*");
    }

    public static String cleanMessageForTranslation(String message) {
        if (message == null) {
            return "";
        }

        // Remove color codes for translation
        String cleaned = stripColors(message);

        // Trim whitespace
        cleaned = cleaned.trim();

        // Remove excessive spaces
        cleaned = cleaned.replaceAll("\\s+", " ");

        return cleaned;
    }
}