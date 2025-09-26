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

public class GoogleTranslateService {

    private final TranslatePlus plugin;
    private final Map<String, String> translationCache;
    private final Map<String, Long> rateLimitTracker;
    private static final String TRANSLATE_API_URL = "https://translation.googleapis.com/language/translate/v2";

    public GoogleTranslateService(TranslatePlus plugin) {
        this.plugin = plugin;
        this.translationCache = new ConcurrentHashMap<>();
        this.rateLimitTracker = new ConcurrentHashMap<>();

        // Start cache cleanup task
        startCacheCleanupTask();
    }

    public boolean validateApiKey() {
        String apiKey = plugin.getConfigManager().getApiKey();
        return apiKey != null && !apiKey.equals("PUT-YOUR-KEY-HERE") && !apiKey.isEmpty();
    }

    public CompletableFuture<String> translateText(String text, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validation checks
                if (!isValidTranslationRequest(text, targetLanguage)) {
                    return text;
                }

                String cacheKey = text + ":" + targetLanguage;

                // Check cache first
                if (translationCache.containsKey(cacheKey)) {
                    plugin.getConfigManager().debugLog("Cache hit for: " + cacheKey);
                    return translationCache.get(cacheKey);
                }

                // Check rate limiting
                if (!checkRateLimit()) {
                    plugin.getConfigManager().debugLog("Rate limit exceeded");
                    return text;
                }

                String apiKey = plugin.getConfigManager().getApiKey();
                if (!validateApiKey()) {
                    return text;
                }

                String result = performTranslation(text, targetLanguage, apiKey);

                // Cache the result if cache isn't full
                if (translationCache.size() < plugin.getConfigManager().getCacheSize()) {
                    translationCache.put(cacheKey, result);
                    plugin.getConfigManager().debugLog("Cached translation: " + cacheKey);
                }

                return result;

            } catch (SocketTimeoutException e) {
                plugin.getLogger().warning("Translation timeout: " + e.getMessage());
                return text;
            } catch (IOException e) {
                if (e.getMessage().contains("quota")) {
                    plugin.getLogger().warning("API quota exceeded");
                    return text;
                }
                plugin.getLogger().warning("Translation failed: " + e.getMessage());
                return text;
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected translation error: " + e.getMessage());
                return text;
            }
        });
    }

    private boolean isValidTranslationRequest(String text, String targetLanguage) {
        // Check if message is valid length
        if (!plugin.getConfigManager().isValidMessageLength(text)) {
            return false;
        }

        // Check if text is blacklisted
        if (plugin.getConfigManager().isBlacklisted(text)) {
            plugin.getConfigManager().debugLog("Text contains blacklisted words: " + text);
            return false;
        }

        // Check if we should skip English text
        if (!plugin.getConfigManager().shouldTranslateEnglishText() &&
                LanguageUtil.appearsToBeEnglish(text)) {
            plugin.getConfigManager().debugLog("Skipping English text: " + text);
            return false;
        }

        // Check if target language is supported
        if (!isLanguageSupported(targetLanguage)) {
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

            plugin.getConfigManager().debugLog("Translation successful: " + processedText + " -> " + translatedText);
            return translatedText;
        }

        throw new IOException("Invalid response from Google Translate API");
    }

    private void startCacheCleanupTask() {
        int cleanupInterval = plugin.getConfigManager().getCacheCleanupInterval();
        if (cleanupInterval > 0) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                int oldSize = translationCache.size();
                // Simple cleanup: remove oldest entries if cache is too large
                if (oldSize > plugin.getConfigManager().getCacheSize()) {
                    translationCache.clear();
                    plugin.getConfigManager().debugLog("Cache cleared - was " + oldSize + " entries");
                }

                // Clean rate limit tracker
                rateLimitTracker.clear();

            }, cleanupInterval * 60 * 20L, cleanupInterval * 60 * 20L); // Convert minutes to ticks
        }
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
        return languages;
    }

    public boolean isLanguageSupported(String languageCode) {
        return getSupportedLanguages().containsKey(languageCode.toLowerCase());
    }

    public void clearCache() {
        translationCache.clear();
        rateLimitTracker.clear();
        plugin.getConfigManager().debugLog("Cache and rate limiter cleared manually");
    }

    public int getCacheSize() {
        return translationCache.size();
    }

    public Map<String, String> getCacheStats() {
        Map<String, String> stats = new HashMap<>();
        stats.put("cache_size", String.valueOf(translationCache.size()));
        stats.put("cache_limit", String.valueOf(plugin.getConfigManager().getCacheSize()));
        stats.put("rate_limit_entries", String.valueOf(rateLimitTracker.size()));
        return stats;
    }
}