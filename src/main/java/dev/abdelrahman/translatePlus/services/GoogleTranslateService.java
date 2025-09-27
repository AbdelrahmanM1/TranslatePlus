package dev.abdelrahman.translatePlus.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;
import dev.abdelrahman.translatePlus.utils.ChatUtil;

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

public class GoogleTranslateService {

    private final TranslatePlus plugin;
    private final Map<String, String> translationCache;
    private final Map<String, Long> rateLimitTracker;
    private final AtomicLong totalTranslations = new AtomicLong(0);
    private final AtomicLong successfulTranslations = new AtomicLong(0);
    private final AtomicLong failedTranslations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    private static final String TRANSLATE_API_URL = "https://translation.googleapis.com/language/translate/v2";
    private volatile boolean shutdownRequested = false;

    public GoogleTranslateService(TranslatePlus plugin) {
        this.plugin = plugin;
        this.translationCache = new ConcurrentHashMap<>();
        this.rateLimitTracker = new ConcurrentHashMap<>();

        // Start cache cleanup task
        startCacheCleanupTask();

        plugin.getConfigManager().debugLog("GoogleTranslateService initialized with cache limit: " +
                plugin.getConfigManager().getCacheSize());
    }

    public boolean validateApiKey() {
        try {
            String apiKey = plugin.getConfigManager().getApiKey();
            boolean isValid = apiKey != null &&
                    !apiKey.equals("PUT-YOUR-KEY-HERE") &&
                    !apiKey.trim().isEmpty() &&
                    apiKey.length() > 10; // Basic length check

            if (isValid) {
                plugin.getConfigManager().debugLog("API key validation passed");
            } else {
                plugin.getConfigManager().debugLog("API key validation failed");
            }

            return isValid;
        } catch (Exception e) {
            plugin.getConfigManager().debugLog("API key validation error: " + e.getMessage());
            return false;
        }
    }

    public CompletableFuture<String> translateText(String text, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            totalTranslations.incrementAndGet();

            try {
                // Validation checks
                if (!isValidTranslationRequest(text, targetLanguage)) {
                    plugin.getConfigManager().debugLog("Invalid translation request rejected");
                    return text; // Return original text if invalid
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
                    plugin.getConfigManager().debugLog("Cache hit for: " +
                            cleanText.substring(0, Math.min(50, cleanText.length())) + "...");
                    return translationCache.get(cacheKey);
                }

                // Check rate limiting
                if (!checkRateLimit()) {
                    plugin.getConfigManager().debugLog("Rate limit exceeded");
                    failedTranslations.incrementAndGet();
                    return text;
                }

                // Validate API key
                if (!validateApiKey()) {
                    plugin.getConfigManager().debugLog("API key validation failed during translation");
                    failedTranslations.incrementAndGet();
                    return text;
                }

                String result = performTranslation(cleanText, targetLanguage);

                // Cache the result if successful and cache isn't full
                if (!result.equals(cleanText) && translationCache.size() < plugin.getConfigManager().getCacheSize()) {
                    translationCache.put(cacheKey, result);
                    plugin.getConfigManager().debugLog("Cached translation result");
                }

                // Track success
                successfulTranslations.incrementAndGet();

                return result;

            } catch (SocketTimeoutException e) {
                plugin.getLogger().warning("Translation timeout: " + e.getMessage());
                failedTranslations.incrementAndGet();
                return text;
            } catch (IOException e) {
                handleIOException(e);
                failedTranslations.incrementAndGet();
                return text;
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected translation error: " + e.getMessage());
                plugin.getConfigManager().debugLog("Translation error details: " + e.toString());
                failedTranslations.incrementAndGet();
                return text;
            }
        });
    }

    private void handleIOException(IOException e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("quota")) {
                plugin.getLogger().warning("Google Translate API quota exceeded");
            } else if (message.contains("403")) {
                plugin.getLogger().warning("Google Translate API access forbidden - check API key and billing");
            } else if (message.contains("429")) {
                plugin.getLogger().warning("Google Translate API rate limit exceeded");
            } else {
                plugin.getLogger().warning("Translation API error: " + message);
            }
        } else {
            plugin.getLogger().warning("Translation failed with IOException: " + e.getClass().getSimpleName());
        }
    }

    private String createCacheKey(String text, String targetLanguage) {
        // Create a more robust cache key
        return targetLanguage + ":" + text.toLowerCase().trim();
    }

    private boolean isValidTranslationRequest(String text, String targetLanguage) {
        // Check if message is valid length
        if (!plugin.getConfigManager().isValidMessageLength(text)) {
            plugin.getConfigManager().debugLog("Invalid message length: " +
                    (text != null ? text.length() : 0));
            return false;
        }

        // Check if text is blacklisted
        if (plugin.getConfigManager().isBlacklisted(text)) {
            plugin.getConfigManager().debugLog("Text contains blacklisted words");
            return false;
        }

        // Check if text is meaningful
        if (!ChatUtil.isValidMessage(text)) {
            plugin.getConfigManager().debugLog("Text is not meaningful for translation");
            return false;
        }

        // Check if we should skip English text
        if (!plugin.getConfigManager().shouldTranslateEnglishText()) {
            try {
                if (LanguageUtil.appearsToBeEnglish(text)) {
                    plugin.getConfigManager().debugLog("Skipping English text");
                    return false;
                }
            } catch (Exception e) {
                plugin.getConfigManager().debugLog("Error in English detection: " + e.getMessage());
                // Continue without this check if LanguageUtil fails
            }
        }

        // Check if target language is supported
        if (!isLanguageSupported(targetLanguage)) {
            plugin.getConfigManager().debugLog("Unsupported target language: " + targetLanguage);
            return false;
        }

        // Check if target language is same as detected source (basic check)
        if ("en".equals(targetLanguage.toLowerCase()) &&
                plugin.getConfigManager().shouldTranslateEnglishText() == false) {
            try {
                if (LanguageUtil.appearsToBeEnglish(text)) {
                    plugin.getConfigManager().debugLog("Skipping translation to English for English text");
                    return false;
                }
            } catch (Exception e) {
                // Ignore error and continue
            }
        }

        return true;
    }

    private boolean checkRateLimit() {
        int rateLimit = plugin.getConfigManager().getRateLimitPerMinute();
        if (rateLimit <= 0) {
            return true; // Rate limiting disabled
        }

        String key = Thread.currentThread().getName();
        long currentTime = System.currentTimeMillis();
        long oneMinuteAgo = currentTime - 60000;

        // Clean old entries first
        rateLimitTracker.entrySet().removeIf(entry -> {
            try {
                long timestamp = Long.parseLong(entry.getKey().split(":", 2)[1]);
                return timestamp < oneMinuteAgo;
            } catch (Exception e) {
                return true; // Remove invalid entries
            }
        });

        // Count recent requests for this thread
        long recentRequests = rateLimitTracker.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(key + ":"))
                .count();

        if (recentRequests >= rateLimit) {
            plugin.getConfigManager().debugLog("Rate limit exceeded: " + recentRequests + "/" + rateLimit);
            return false;
        }

        // Track this request
        rateLimitTracker.put(key + ":" + currentTime, currentTime);
        return true;
    }

    private String performTranslation(String text, String targetLanguage) throws IOException {
        String apiKey = plugin.getConfigManager().getApiKey();

        // Truncate message if too long
        String processedText = plugin.getConfigManager().truncateMessage(text);

        URL url = new URL(TRANSLATE_API_URL + "?key=" + apiKey);
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();

            // Apply timeouts from config
            conn.setConnectTimeout(plugin.getConfigManager().getConnectionTimeout());
            conn.setReadTimeout(plugin.getConfigManager().getReadTimeout());

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "TranslatePlus/2.0 (Minecraft Plugin)");
            conn.setDoOutput(true);

            // Create request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("q", processedText);
            requestBody.addProperty("target", targetLanguage);
            requestBody.addProperty("format", "text");
            requestBody.addProperty("model", "base"); // Use base model for better performance

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response code
            int responseCode = conn.getResponseCode();
            if (responseCode == 403) {
                throw new IOException("API key invalid or quota exceeded");
            } else if (responseCode == 400) {
                throw new IOException("Bad request - invalid parameters");
            } else if (responseCode == 429) {
                throw new IOException("Rate limit exceeded");
            } else if (responseCode != 200) {
                // Read error response for more details
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

            // Parse JSON response
            return parseTranslationResponse(response.toString(), processedText);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
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

    private String parseTranslationResponse(String responseJson, String originalText) throws IOException {
        try {
            JsonParser parser = new JsonParser();
            JsonObject jsonResponse = parser.parse(responseJson).getAsJsonObject();

            if (jsonResponse.has("data") && jsonResponse.get("data").getAsJsonObject().has("translations")) {
                String translatedText = jsonResponse.get("data").getAsJsonObject()
                        .get("translations").getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .get("translatedText").getAsString();

                // Validate translated text
                if (translatedText == null || translatedText.trim().isEmpty()) {
                    plugin.getConfigManager().debugLog("Empty translation received");
                    return originalText;
                }

                plugin.getConfigManager().debugLog("Translation successful: " +
                        originalText.substring(0, Math.min(30, originalText.length())) + "... -> " +
                        translatedText.substring(0, Math.min(30, translatedText.length())) + "...");

                return translatedText.trim();
            }

            plugin.getConfigManager().debugLog("Invalid response structure: " + responseJson);
            throw new IOException("Invalid response structure from Google Translate API");

        } catch (JsonSyntaxException e) {
            plugin.getConfigManager().debugLog("JSON parsing error: " + e.getMessage());
            throw new IOException("Invalid JSON response from Google Translate API");
        }
    }

    private void startCacheCleanupTask() {
        int cleanupInterval = plugin.getConfigManager().getCacheCleanupInterval();
        if (cleanupInterval <= 0) {
            plugin.getConfigManager().debugLog("Cache cleanup disabled");
            return;
        }

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (shutdownRequested) return;

            try {
                performCacheCleanup();
            } catch (Exception e) {
                plugin.getLogger().warning("Error during cache cleanup: " + e.getMessage());
            }
        }, cleanupInterval * 60 * 20L, cleanupInterval * 60 * 20L); // Convert minutes to ticks
    }

    private void performCacheCleanup() {
        int oldSize = translationCache.size();
        int maxSize = plugin.getConfigManager().getCacheSize();

        // Simple cleanup: remove oldest entries if cache is too large
        if (oldSize > maxSize) {
            translationCache.clear(); // Simple approach - clear all if over limit
            plugin.getConfigManager().debugLog("Cache cleared - was " + oldSize + " entries, max is " + maxSize);
        }

        // Clean rate limit tracker
        long currentTime = System.currentTimeMillis();
        long fiveMinutesAgo = currentTime - 300000; // 5 minutes

        int rateLimitSize = rateLimitTracker.size();
        rateLimitTracker.entrySet().removeIf(entry -> {
            try {
                long timestamp = Long.parseLong(entry.getKey().split(":", 2)[1]);
                return timestamp < fiveMinutesAgo;
            } catch (Exception e) {
                return true; // Remove invalid entries
            }
        });

        if (rateLimitSize != rateLimitTracker.size()) {
            plugin.getConfigManager().debugLog("Rate limit tracker cleaned: " +
                    (rateLimitSize - rateLimitTracker.size()) + " entries removed");
        }
    }

    /**
     * Perform maintenance tasks - called by the main class scheduler
     */
    public void performMaintenance() {
        if (shutdownRequested) return;

        plugin.getConfigManager().debugLog("Performing translation service maintenance");

        // Log statistics
        if (plugin.getConfigManager().isDebugModeEnabled()) {
            plugin.getLogger().info("=== Translation Service Statistics ===");
            plugin.getLogger().info("Total translations: " + totalTranslations.get());
            plugin.getLogger().info("Successful: " + successfulTranslations.get());
            plugin.getLogger().info("Failed: " + failedTranslations.get());
            plugin.getLogger().info("Cache hits: " + cacheHits.get());
            plugin.getLogger().info("Cache size: " + translationCache.size() + "/" + plugin.getConfigManager().getCacheSize());
            plugin.getLogger().info("Rate limit entries: " + rateLimitTracker.size());
            plugin.getLogger().info("Success rate: " + String.format("%.1f%%", getSuccessRate()));
        }

        // Force cleanup if needed
        performCacheCleanup();
    }

    /**
     * Shutdown the service gracefully
     */
    public void shutdown() {
        shutdownRequested = true;
        clearCache();
        plugin.getConfigManager().debugLog("GoogleTranslateService shutdown completed");
    }

    public Map<String, String> getSupportedLanguages() {
        Map<String, String> languages = new HashMap<>();
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
        plugin.getConfigManager().debugLog("Cache and rate limiter cleared manually - removed " + oldSize + " entries");
    }

    public int getCacheSize() {
        return translationCache.size();
    }

    public Map<String, String> getCacheStats() {
        Map<String, String> stats = new HashMap<>();
        stats.put("cache_size", String.valueOf(translationCache.size()));
        stats.put("cache_limit", String.valueOf(plugin.getConfigManager().getCacheSize()));
        stats.put("rate_limit_entries", String.valueOf(rateLimitTracker.size()));
        stats.put("total_translations", String.valueOf(totalTranslations.get()));
        stats.put("successful_translations", String.valueOf(successfulTranslations.get()));
        stats.put("failed_translations", String.valueOf(failedTranslations.get()));
        stats.put("cache_hits", String.valueOf(cacheHits.get()));
        stats.put("cache_hit_ratio", calculateCacheHitRatio());
        stats.put("success_rate", String.format("%.1f%%", getSuccessRate()));
        return stats;
    }

    private String calculateCacheHitRatio() {
        long total = totalTranslations.get();
        long hits = cacheHits.get();

        if (total == 0) return "0%";

        double ratio = ((double) hits / total) * 100;
        return String.format("%.1f%%", ratio);
    }

    /**
     * Get service health status
     */
    public boolean isHealthy() {
        return !shutdownRequested &&
                plugin.getConfigManager().isApiKeyConfigured() &&
                translationCache.size() <= plugin.getConfigManager().getCacheSize() * 2 &&
                getSuccessRate() > 50.0; // At least 50% success rate
    }

    /**
     * Get translation success rate
     */
    public double getSuccessRate() {
        long total = totalTranslations.get();
        if (total == 0) return 100.0;

        long successful = successfulTranslations.get();
        return (double) successful / total * 100.0;
    }
}