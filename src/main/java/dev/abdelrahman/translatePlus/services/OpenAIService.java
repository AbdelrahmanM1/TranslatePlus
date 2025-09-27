package dev.abdelrahman.translatePlus.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.ChatUtil;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenAI-powered translation service as an alternative to Google Translate
 * This service uses OpenAI's ChatGPT API for translation
 */
public class OpenAIService {

    private final TranslatePlus plugin;
    private final Map<String, String> translationCache;
    private final Map<String, Long> rateLimitTracker;
    private final AtomicLong totalTranslations = new AtomicLong(0);
    private final AtomicLong successfulTranslations = new AtomicLong(0);
    private final AtomicLong failedTranslations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private volatile boolean shutdownRequested = false;

    public OpenAIService(TranslatePlus plugin) {
        this.plugin = plugin;
        this.translationCache = new ConcurrentHashMap<>();
        this.rateLimitTracker = new ConcurrentHashMap<>();

        plugin.getConfigManager().debugLog("OpenAIService initialized as translation provider");
    }

    /**
     * Validates if the OpenAI API key is properly configured
     */
    public boolean validateApiKey() {
        try {
            String apiKey = getApiKey();
            boolean isValid = apiKey != null &&
                    !apiKey.equals("PUT-YOUR-OPENAI-KEY-HERE") &&
                    !apiKey.trim().isEmpty() &&
                    apiKey.startsWith("sk-") && // OpenAI keys start with sk-
                    apiKey.length() > 20;

            if (isValid) {
                plugin.getConfigManager().debugLog("OpenAI API key validation passed");
            } else {
                plugin.getConfigManager().debugLog("OpenAI API key validation failed");
            }

            return isValid;
        } catch (Exception e) {
            plugin.getConfigManager().debugLog("OpenAI API key validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the OpenAI API key from configuration
     */
    private String getApiKey() {
        // Try to get OpenAI-specific key first, fall back to google key field if needed
        String openaiKey = plugin.getConfig().getString("openai-api-key");
        if (openaiKey != null && !openaiKey.equals("PUT-YOUR-OPENAI-KEY-HERE")) {
            return openaiKey;
        }

        // Fallback to reusing the google-api-key field
        return plugin.getConfigManager().getApiKey();
    }

    public CompletableFuture<String> translateText(String text, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            totalTranslations.incrementAndGet();

            try {
                // Validation checks
                if (!isValidTranslationRequest(text, targetLanguage)) {
                    plugin.getConfigManager().debugLog("Invalid OpenAI translation request rejected");
                    return text;
                }

                // Clean text for translation
                String cleanText = ChatUtil.cleanMessageForTranslation(text);
                if (cleanText.isEmpty()) {
                    plugin.getConfigManager().debugLog("Text became empty after cleaning");
                    return text;
                }

                String cacheKey = createCacheKey(cleanText, targetLanguage);

                // Check cache first
                if (translationCache.containsKey(cacheKey)) {
                    cacheHits.incrementAndGet();
                    plugin.getConfigManager().debugLog("OpenAI cache hit for: " +
                            cleanText.substring(0, Math.min(50, cleanText.length())) + "...");
                    return translationCache.get(cacheKey);
                }

                // Check rate limiting
                if (!checkRateLimit()) {
                    plugin.getConfigManager().debugLog("OpenAI rate limit exceeded");
                    failedTranslations.incrementAndGet();
                    return text;
                }

                // Validate API key
                if (!validateApiKey()) {
                    plugin.getConfigManager().debugLog("OpenAI API key validation failed during translation");
                    failedTranslations.incrementAndGet();
                    return text;
                }

                String result = performTranslation(cleanText, targetLanguage);

                // Cache the result if successful and cache isn't full
                if (!result.equals(cleanText) && translationCache.size() < plugin.getConfigManager().getCacheSize()) {
                    translationCache.put(cacheKey, result);
                    plugin.getConfigManager().debugLog("Cached OpenAI translation result");
                }

                successfulTranslations.incrementAndGet();
                return result;

            } catch (SocketTimeoutException e) {
                plugin.getLogger().warning("OpenAI translation timeout: " + e.getMessage());
                failedTranslations.incrementAndGet();
                return text;
            } catch (IOException e) {
                handleIOException(e);
                failedTranslations.incrementAndGet();
                return text;
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected OpenAI translation error: " + e.getMessage());
                plugin.getConfigManager().debugLog("OpenAI translation error details: " + e.toString());
                failedTranslations.incrementAndGet();
                return text;
            }
        });
    }

    private void handleIOException(IOException e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("401")) {
                plugin.getLogger().warning("OpenAI API authentication failed - check API key");
            } else if (message.contains("429")) {
                plugin.getLogger().warning("OpenAI API rate limit exceeded");
            } else if (message.contains("quota")) {
                plugin.getLogger().warning("OpenAI API quota exceeded");
            } else {
                plugin.getLogger().warning("OpenAI API error: " + message);
            }
        } else {
            plugin.getLogger().warning("OpenAI translation failed with IOException: " + e.getClass().getSimpleName());
        }
    }

    private String createCacheKey(String text, String targetLanguage) {
        return "openai:" + targetLanguage + ":" + text.toLowerCase().trim();
    }

    private boolean isValidTranslationRequest(String text, String targetLanguage) {
        // Use the same validation as Google Translate service
        if (!plugin.getConfigManager().isValidMessageLength(text)) {
            return false;
        }

        if (plugin.getConfigManager().isBlacklisted(text)) {
            return false;
        }

        if (!ChatUtil.isValidMessage(text)) {
            return false;
        }

        // Check English detection if enabled
        if (!plugin.getConfigManager().shouldTranslateEnglishText()) {
            try {
                if (LanguageUtil.appearsToBeEnglish(text)) {
                    plugin.getConfigManager().debugLog("Skipping English text for OpenAI");
                    return false;
                }
            } catch (Exception e) {
                plugin.getConfigManager().debugLog("Error in English detection: " + e.getMessage());
            }
        }

        // Check if target language is supported
        if (!isLanguageSupported(targetLanguage)) {
            plugin.getConfigManager().debugLog("Unsupported target language for OpenAI: " + targetLanguage);
            return false;
        }

        return true;
    }

    private boolean checkRateLimit() {
        int rateLimit = plugin.getConfigManager().getRateLimitPerMinute();
        if (rateLimit <= 0) {
            return true;
        }

        String key = "openai:" + Thread.currentThread().getName();
        long currentTime = System.currentTimeMillis();
        long oneMinuteAgo = currentTime - 60000;

        // Clean old entries
        rateLimitTracker.entrySet().removeIf(entry -> {
            try {
                long timestamp = Long.parseLong(entry.getKey().split(":", 3)[2]);
                return timestamp < oneMinuteAgo;
            } catch (Exception e) {
                return true;
            }
        });

        // Count recent requests
        long recentRequests = rateLimitTracker.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(key + ":"))
                .count();

        if (recentRequests >= rateLimit) {
            plugin.getConfigManager().debugLog("OpenAI rate limit exceeded: " + recentRequests + "/" + rateLimit);
            return false;
        }

        rateLimitTracker.put(key + ":" + currentTime, currentTime);
        return true;
    }

    private String performTranslation(String text, String targetLanguage) throws IOException {
        String apiKey = getApiKey();
        String processedText = plugin.getConfigManager().truncateMessage(text);

        // Get language name for better prompt
        String languageName = LanguageUtil.getLanguageName(targetLanguage, getSupportedLanguages());

        URL url = new URL(OPENAI_API_URL);
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();

            // Apply timeouts from config
            conn.setConnectTimeout(plugin.getConfigManager().getConnectionTimeout());
            conn.setReadTimeout(plugin.getConfigManager().getReadTimeout());

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("User-Agent", "TranslatePlus/2.0 (Minecraft Plugin)");
            conn.setDoOutput(true);

            // Create request body
            JsonObject requestBody = createOpenAIRequest(processedText, languageName, targetLanguage);

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response code
            int responseCode = conn.getResponseCode();
            if (responseCode == 401) {
                throw new IOException("OpenAI API authentication failed");
            } else if (responseCode == 429) {
                throw new IOException("OpenAI API rate limit exceeded");
            } else if (responseCode != 200) {
                String errorMessage = readErrorResponse(conn);
                throw new IOException("HTTP " + responseCode + ": " + errorMessage);
            }

            // Read successful response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            return parseOpenAIResponse(response.toString(), processedText);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private JsonObject createOpenAIRequest(String text, String languageName, String targetLanguage) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-3.5-turbo"); // More cost-effective than gpt-4

        JsonArray messages = new JsonArray();

        // System message to set up the translation task
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content",
                "You are a professional translator. Translate the given text accurately to " +
                        languageName + " (" + targetLanguage + "). " +
                        "Only return the translated text, nothing else. " +
                        "Preserve the original tone and meaning. " +
                        "If the text is already in the target language, return it unchanged.");

        messages.add(systemMessage);

        // User message with the text to translate
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", text);

        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("max_tokens", 150); // Reasonable limit for chat messages
        requestBody.addProperty("temperature", 0.1); // Low temperature for consistent translations

        return requestBody;
    }

    private String readErrorResponse(HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                errorResponse.append(line);
            }
            return errorResponse.toString();
        } catch (Exception e) {
            return "Unknown error";
        }
    }

    private String parseOpenAIResponse(String responseJson, String originalText) throws IOException {
        try {
            JsonParser parser = new JsonParser();
            JsonObject jsonResponse = parser.parse(responseJson).getAsJsonObject();

            if (jsonResponse.has("choices") && jsonResponse.get("choices").getAsJsonArray().size() > 0) {
                JsonObject choice = jsonResponse.get("choices").getAsJsonArray().get(0).getAsJsonObject();

                if (choice.has("message")) {
                    String translatedText = choice.get("message").getAsJsonObject()
                            .get("content").getAsString().trim();

                    // Validate translated text
                    if (translatedText.isEmpty()) {
                        plugin.getConfigManager().debugLog("Empty translation received from OpenAI");
                        return originalText;
                    }

                    plugin.getConfigManager().debugLog("OpenAI translation successful: " +
                            originalText.substring(0, Math.min(30, originalText.length())) + "... -> " +
                            translatedText.substring(0, Math.min(30, translatedText.length())) + "...");

                    return translatedText;
                }
            }

            plugin.getConfigManager().debugLog("Invalid OpenAI response structure: " + responseJson);
            throw new IOException("Invalid response structure from OpenAI API");

        } catch (JsonSyntaxException e) {
            plugin.getConfigManager().debugLog("OpenAI JSON parsing error: " + e.getMessage());
            throw new IOException("Invalid JSON response from OpenAI API");
        }
    }

    /**
     * Get supported languages - OpenAI supports many more languages than Google Translate
     */
    public Map<String, String> getSupportedLanguages() {
        Map<String, String> languages = new HashMap<>();

        // Major languages
        languages.put("en", "English");
        languages.put("es", "Spanish");
        languages.put("fr", "French");
        languages.put("de", "German");
        languages.put("it", "Italian");
        languages.put("pt", "Portuguese");
        languages.put("ru", "Russian");
        languages.put("ja", "Japanese");
        languages.put("ko", "Korean");
        languages.put("zh", "Chinese");
        languages.put("ar", "Arabic");
        languages.put("hi", "Hindi");
        languages.put("tr", "Turkish");
        languages.put("pl", "Polish");
        languages.put("nl", "Dutch");
        languages.put("sv", "Swedish");
        languages.put("da", "Danish");
        languages.put("no", "Norwegian");
        languages.put("fi", "Finnish");
        languages.put("he", "Hebrew");
        languages.put("th", "Thai");
        languages.put("vi", "Vietnamese");
        languages.put("id", "Indonesian");
        languages.put("ms", "Malay");
        languages.put("tl", "Filipino");
        languages.put("uk", "Ukrainian");
        languages.put("cs", "Czech");
        languages.put("hu", "Hungarian");
        languages.put("ro", "Romanian");
        languages.put("bg", "Bulgarian");
        languages.put("hr", "Croatian");
        languages.put("sk", "Slovak");
        languages.put("sl", "Slovenian");
        languages.put("et", "Estonian");
        languages.put("lv", "Latvian");
        languages.put("lt", "Lithuanian");
        languages.put("ca", "Catalan");
        languages.put("eu", "Basque");
        languages.put("gl", "Galician");
        languages.put("cy", "Welsh");
        languages.put("ga", "Irish");
        languages.put("mt", "Maltese");
        languages.put("is", "Icelandic");

        // Additional languages that OpenAI supports well
        languages.put("bn", "Bengali");
        languages.put("fa", "Persian");
        languages.put("ur", "Urdu");
        languages.put("sw", "Swahili");
        languages.put("am", "Amharic");
        languages.put("my", "Myanmar");
        languages.put("ne", "Nepali");
        languages.put("si", "Sinhala");
        languages.put("ka", "Georgian");
        languages.put("hy", "Armenian");
        languages.put("az", "Azerbaijani");
        languages.put("kk", "Kazakh");
        languages.put("ky", "Kyrgyz");
        languages.put("mn", "Mongolian");
        languages.put("uz", "Uzbek");

        return languages;
    }

    public boolean isLanguageSupported(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }
        return getSupportedLanguages().containsKey(languageCode.toLowerCase().trim());
    }

    public void clearCache() {
        int oldSize = translationCache.size() + rateLimitTracker.size();
        translationCache.clear();
        rateLimitTracker.clear();
        plugin.getConfigManager().debugLog("OpenAI cache and rate limiter cleared - removed " + oldSize + " entries");
    }

    public int getCacheSize() {
        return translationCache.size();
    }

    public void performMaintenance() {
        if (shutdownRequested) return;

        plugin.getConfigManager().debugLog("Performing OpenAI service maintenance");

        if (plugin.getConfigManager().isDebugModeEnabled()) {
            plugin.getLogger().info("=== OpenAI Service Statistics ===");
            plugin.getLogger().info("Total translations: " + totalTranslations.get());
            plugin.getLogger().info("Successful: " + successfulTranslations.get());
            plugin.getLogger().info("Failed: " + failedTranslations.get());
            plugin.getLogger().info("Cache hits: " + cacheHits.get());
            plugin.getLogger().info("Cache size: " + translationCache.size());
            plugin.getLogger().info("Success rate: " + String.format("%.1f%%", getSuccessRate()));
        }

        // Clean up cache if needed
        int maxSize = plugin.getConfigManager().getCacheSize();
        if (translationCache.size() > maxSize) {
            translationCache.clear();
            plugin.getConfigManager().debugLog("OpenAI cache cleared - exceeded limit");
        }
    }

    public void shutdown() {
        shutdownRequested = true;
        clearCache();
        plugin.getConfigManager().debugLog("OpenAI service shutdown completed");
    }

    public boolean isHealthy() {
        return !shutdownRequested &&
                validateApiKey() &&
                translationCache.size() <= plugin.getConfigManager().getCacheSize() * 2 &&
                getSuccessRate() > 50.0;
    }

    public double getSuccessRate() {
        long total = totalTranslations.get();
        if (total == 0) return 100.0;

        long successful = successfulTranslations.get();
        return (double) successful / total * 100.0;
    }

    public Map<String, String> getCacheStats() {
        Map<String, String> stats = new HashMap<>();
        stats.put("service", "OpenAI");
        stats.put("cache_size", String.valueOf(translationCache.size()));
        stats.put("cache_limit", String.valueOf(plugin.getConfigManager().getCacheSize()));
        stats.put("total_translations", String.valueOf(totalTranslations.get()));
        stats.put("successful_translations", String.valueOf(successfulTranslations.get()));
        stats.put("failed_translations", String.valueOf(failedTranslations.get()));
        stats.put("cache_hits", String.valueOf(cacheHits.get()));
        stats.put("success_rate", String.format("%.1f%%", getSuccessRate()));
        return stats;
    }
}