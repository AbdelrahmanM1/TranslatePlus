package dev.abdelrahman.translatePlus.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
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

    private static final String TRANSLATE_API_URL = "https://translation.googleapis.com/language/translate/v2";
    private boolean shutdownRequested = false;

    public GoogleTranslateService(TranslatePlus plugin) {
        this.plugin = plugin;
        this.translationCache = new ConcurrentHashMap<>();
        this.rateLimitTracker = new ConcurrentHashMap<>();

        // Start cache cleanup task
        startCacheCleanupTask();

        plugin.getConfigManager().debugLog("GoogleTranslateService initialized with cache size: " + plugin.getConfigManager().getCacheSize());
    }

    public boolean validateApiKey() {
        String apiKey = plugin.getConfigManager().getApiKey();
        boolean isValid = apiKey != null && !apiKey.equals("PUT-YOUR-KEY-HERE") && !apiKey.trim().isEmpty();

        if (isValid) {
            plugin.getConfigManager().debugLog("API key validation passed");
        } else {
            plugin.getConfigManager().debugLog("API key validation failed");
        }

        return isValid;
    }

    public CompletableFuture<String> translateText(String text, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            totalTranslations.incrementAndGet();

            try {
                // Validation checks
                if (!isValidTranslationRequest(text, targetLanguage)) {
                    return text;
                }

                String cacheKey = text + ":" + targetLanguage;

                // Check cache first
                if (translationCache.containsKey(cacheKey)) {
                    plugin.getConfigManager().debugLog("Cache hit for: " + cacheKey.substring(0, Math.min(50, cacheKey.length())) + "...");
                    return translationCache.get(cacheKey);
                }

                // Check rate limiting
                if (!checkRateLimit()) {
                    plugin.getConfigManager().debugLog("Rate limit exceeded");
                    failedTranslations.incrementAndGet();
                    return text;
                }

                String apiKey = plugin.getConfigManager().getApiKey();
                if (!validateApiKey()) {
                    failedTranslations.incrementAndGet();
                    return text;
                }

                String result = performTranslation(text, targetLanguage, apiKey);

                // Cache the result if cache isn't full
                if (translationCache.size() < plugin.getConfigManager().getCacheSize()) {
                    translationCache.put(cacheKey, result);
                    plugin.getConfigManager().debugLog("Cached translation result");
                }

                // Track player translation count
                plugin.getPlayerManager().incrementTranslationCount();
                successfulTranslations.incrementAndGet();

                return result;

            } catch (SocketTimeoutException e) {
                plugin.getLogger().warning("Translation timeout: " + e.getMessage());
                failedTranslations.incrementAndGet();
                return text;
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("quota")) {
                    plugin.getLogger().warning("API quota exceeded");
                } else {
                    plugin.getLogger().warning("Translation failed: " + e.getMessage());
                }
                failedTranslations.incrementAndGet();
                return text;
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected translation error: " + e.getMessage());
                plugin.getConfigManager().debugLog("Translation error stack trace: " + e.toString());
                failedTranslations.incrementAndGet();
                return text;
            }
        });
    }

    private boolean isValidTranslationRequest(String text, String targetLanguage) {
        // Check if message is valid length
        if (!plugin.getConfigManager().isValidMessageLength(text)) {
            plugin.getConfigManager().debugLog("Invalid message length: " + text.length());
            return false;
        }

        // Check if text is blacklisted
        if (plugin.getConfigManager().isBlacklisted(text)) {
            plugin.getConfigManager().debugLog("Text contains blacklisted words");
            return false;
        }

        // Check if we should skip English text (only if LanguageUtil exists)
        if (!plugin.getConfigManager().shouldTranslateEnglishText()) {
            try {
                if (LanguageUtil.appearsToBeEnglish(text)) {
                    plugin.getConfigManager().debugLog("Skipping English text");
                    return false;
                }
            } catch (Exception e) {
                // LanguageUtil might not exist, continue without this check
                plugin.getConfigManager().debugLog("LanguageUtil not available, skipping English detection");
            }
        }

        // Check if target language is supported
        if (!isLanguageSupported(targetLanguage)) {
            plugin.getConfigManager().debugLog("Unsupported target language: " + targetLanguage);
            return false;
        }

        return true;
    }

    private boolean checkRateLimit() {
        int rateLimit = plugin.getConfigManager().getRateLimitPerMinute();
        if (rateLimit <= 0) {
            return true; // Rate limiting disabled
        }

        String key = Thread.currentThread().getName(); // Simple rate limiting per thread
        long currentTime = System.currentTimeMillis();
        long oneMinuteAgo = currentTime - 60000;

        // Clean old entries
        rateLimitTracker.entrySet().removeIf(entry ->
                Long.parseLong(entry.getKey().split(":")[1]) < oneMinuteAgo);

        // Count recent requests
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

    private String performTranslation(String text, String targetLanguage, String apiKey) throws IOException {
        // Truncate message if too long
        String processedText = plugin.getConfigManager().truncateMessage(text);

        URL url = new URL(TRANSLATE_API_URL + "?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Apply timeouts from config
        conn.setConnectTimeout(plugin.getConfigManager().getConnectionTimeout());
        conn.setReadTimeout(plugin.getConfigManager().getReadTimeout());

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "TranslatePlus/1.0");
        conn.setDoOutput(true);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("q", processedText);
        requestBody.addProperty("target", targetLanguage);
        requestBody.addProperty("format", "text");
        requestBody.addProperty("model", "base"); // Use base model for better performance

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Check response code
        int responseCode = conn.getResponseCode();
        if (responseCode == 403) {
            throw new IOException("quota");
        } else if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + ": " + conn.getResponseMessage());
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        // Parse JSON response
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response.toString()).getAsJsonObject();

        if (jsonResponse.has("data") && jsonResponse.get("data").getAsJsonObject().has("translations")) {
            String translatedText = jsonResponse.get("data").getAsJsonObject()
                    .get("translations").getAsJsonArray()
                    .get(0).getAsJsonObject()
                    .get("translatedText").getAsString();

            plugin.getConfigManager().debugLog("Translation successful: " +
                    processedText.substring(0, Math.min(30, processedText.length())) + "... -> " +
                    translatedText.substring(0, Math.min(30, translatedText.length())) + "...");

            return translatedText;
        }

        throw new IOException("Invalid response from Google Translate API");
    }

    private void startCacheCleanupTask() {
        int cleanupInterval = plugin.getConfigManager().getCacheCleanupInterval();
        if (cleanupInterval > 0) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (shutdownRequested) return;

                int oldSize = translationCache.size();
                int maxSize = plugin.getConfigManager().getCacheSize();

                // Simple cleanup: remove oldest entries if cache is too large
                if (oldSize > maxSize) {
                    translationCache.clear();
                    plugin.getConfigManager().debugLog("Cache cleared - was " + oldSize + " entries, max is " + maxSize);
                }

                // Clean rate limit tracker
                long currentTime = System.currentTimeMillis();
                long fiveMinutesAgo = currentTime - 300000; // 5 minutes

                int rateLimitSize = rateLimitTracker.size();
                rateLimitTracker.entrySet().removeIf(entry -> {
                    try {
                        long timestamp = Long.parseLong(entry.getKey().split(":")[1]);
                        return timestamp < fiveMinutesAgo;
                    } catch (Exception e) {
                        return true; // Remove invalid entries
                    }
                });

                if (rateLimitSize != rateLimitTracker.size()) {
                    plugin.getConfigManager().debugLog("Rate limit tracker cleaned: " +
                            (rateLimitSize - rateLimitTracker.size()) + " entries removed");
                }

            }, cleanupInterval * 60 * 20L, cleanupInterval * 60 * 20L); // Convert minutes to ticks
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
            plugin.getLogger().info("=== Translation Service Stats ===");
            plugin.getLogger().info("Total translations: " + totalTranslations.get());
            plugin.getLogger().info("Successful: " + successfulTranslations.get());
            plugin.getLogger().info("Failed: " + failedTranslations.get());
            plugin.getLogger().info("Cache size: " + translationCache.size() + "/" + plugin.getConfigManager().getCacheSize());
            plugin.getLogger().info("Rate limit entries: " + rateLimitTracker.size());
        }

        // Force cleanup if needed
        int cacheSize = translationCache.size();
        int maxCacheSize = plugin.getConfigManager().getCacheSize();

        if (cacheSize > maxCacheSize * 1.2) { // If 20% over limit
            plugin.getConfigManager().debugLog("Force cleaning cache: " + cacheSize + " > " + maxCacheSize);
            translationCache.clear();
        }
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
        stats.put("cache_hit_ratio", calculateCacheHitRatio());
        return stats;
    }

    private String calculateCacheHitRatio() {
        long total = totalTranslations.get();
        if (total == 0) return "0%";

        // Approximate cache hits (this is a simplified calculation)
        long successful = successfulTranslations.get();
        if (successful == 0) return "0%";

        double ratio = ((double) translationCache.size() / successful) * 100;
        return String.format("%.1f%%", Math.min(ratio, 100.0));
    }

    /**
     * Get service health status
     */
    public boolean isHealthy() {
        return !shutdownRequested &&
                plugin.getConfigManager().isApiKeyConfigured() &&
                translationCache.size() <= plugin.getConfigManager().getCacheSize() * 2;
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