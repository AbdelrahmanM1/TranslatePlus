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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * OpenAI-powered translation service with improved error handling,
 * better performance, and additional features for chat translation
 */
public class OpenAIService {

    private final TranslatePlus plugin;
    private final Map<String, TranslationCacheEntry> translationCache;
    private final Map<String, Long> rateLimitTracker;
    private final Map<String, Integer> errorTracker;

    // Performance metrics
    private final AtomicLong totalTranslations = new AtomicLong(0);
    private final AtomicLong successfulTranslations = new AtomicLong(0);
    private final AtomicLong failedTranslations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong apiCalls = new AtomicLong(0);
    private final AtomicLong totalCost = new AtomicLong(0); // Track estimated costs in microdollars

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("^sk-[A-Za-z0-9]{48}$");
    private static final long CACHE_EXPIRY_TIME = TimeUnit.HOURS.toMillis(24); // 24 hours cache
    private static final int MAX_RETRIES = 2;
    private static final int BASE_RETRY_DELAY = 1000; // 1 second

    private volatile boolean shutdownRequested = false;
    private long lastMaintenanceTime = System.currentTimeMillis();

    public OpenAIService(TranslatePlus plugin) {
        this.plugin = plugin;
        this.translationCache = new ConcurrentHashMap<>();
        this.rateLimitTracker = new ConcurrentHashMap<>();
        this.errorTracker = new ConcurrentHashMap<>();

        plugin.getConfigManager().debugLog(" OpenAI Service initialized");
    }

    /**
     * Cache entry with expiration time
     */
    private static class TranslationCacheEntry {
        private final String translation;
        private final long timestamp;
        private final int tokenCount;

        public TranslationCacheEntry(String translation, int tokenCount) {
            this.translation = translation;
            this.timestamp = System.currentTimeMillis();
            this.tokenCount = tokenCount;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
        }

        public String getTranslation() {
            return translation;
        }

        public int getTokenCount() {
            return tokenCount;
        }
    }

    /**
     *  API key validation with better pattern matching
     */
    public boolean validateApiKey() {
        try {
            String apiKey = getApiKey();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                plugin.getConfigManager().debugLog("OpenAI API key is null or empty");
                return false;
            }

            apiKey = apiKey.trim();

            if (apiKey.equals("PUT-YOUR-OPENAI-KEY-HERE") || apiKey.equals("PUT-YOUR-GOOGLE-KEY-HERE")) {
                plugin.getConfigManager().debugLog("OpenAI API key is default placeholder: " + apiKey);
                return false;
            }

            // More flexible key validation - OpenAI keys can vary in length
            // Updated pattern to handle different OpenAI key formats
            boolean isValidFormat = apiKey.startsWith("sk-") && apiKey.length() >= 20;

            // Additional validation - check for reasonable key structure
            if (isValidFormat) {
                // OpenAI keys typically have specific patterns after sk-
                // But let's be more flexible than the original rigid pattern
                boolean hasValidStructure = apiKey.matches("^sk-[A-Za-z0-9\\-_]{20,}$");

                if (hasValidStructure) {
                    plugin.getConfigManager().debugLog("OpenAI API key validation passed (length: " + apiKey.length() + ")");
                    return true;
                } else {
                    plugin.getConfigManager().debugLog("OpenAI API key structure validation failed. Key: " +
                            apiKey.substring(0, Math.min(10, apiKey.length())) + "... (length: " + apiKey.length() + ")");
                }
            } else {
                plugin.getConfigManager().debugLog("OpenAI API key format validation failed. Key starts with: " +
                        apiKey.substring(0, Math.min(10, apiKey.length())) + "... (length: " + apiKey.length() + ")");
            }

            return false;

        } catch (Exception e) {
            plugin.getConfigManager().debugLog("OpenAI API key validation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the OpenAI API key with fallback logic
     */
    private String getApiKey() {
        // Primary: OpenAI-specific key
        String openaiKey = plugin.getConfig().getString("openai-api-key");
        if (openaiKey != null &&
                !openaiKey.equals("PUT-YOUR-OPENAI-KEY-HERE") &&
                !openaiKey.trim().isEmpty()) {
            return openaiKey.trim();
        }

        // Fallback: Google API key field (for backwards compatibility)
        String fallbackKey = plugin.getConfigManager().getApiKey();
        if (fallbackKey != null &&
                !fallbackKey.equals("PUT-YOUR-GOOGLE-KEY-HERE") &&
                !fallbackKey.trim().isEmpty() &&
                OPENAI_KEY_PATTERN.matcher(fallbackKey.trim()).matches()) {
            return fallbackKey.trim();
        }

        return null;
    }

    /**
     * translation method with better error handling and retry logic
     */
    public CompletableFuture<String> translateText(String text, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            totalTranslations.incrementAndGet();

            try {
                // validation
                if (!isValidTranslationRequest(text, targetLanguage)) {
                    plugin.getConfigManager().debugLog("Invalid OpenAI translation request rejected");
                    return text;
                }

                // Clean and prepare text
                String cleanText = ChatUtil.cleanMessageForTranslation(text);
                if (cleanText.isEmpty()) {
                    plugin.getConfigManager().debugLog("Text became empty after cleaning");
                    return text;
                }

                String cacheKey = createCacheKey(cleanText, targetLanguage);

                // Check cache with expiration
                TranslationCacheEntry cached = translationCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    cacheHits.incrementAndGet();
                    plugin.getConfigManager().debugLog("OpenAI cache hit for: " +
                            cleanText.substring(0, Math.min(50, cleanText.length())) + "...");
                    return cached.getTranslation();
                }

                // Remove expired cache entry if present
                if (cached != null && cached.isExpired()) {
                    translationCache.remove(cacheKey);
                }

                // Rate limiting and API key validation
                if (!checkRateLimit() || !validateApiKey()) {
                    failedTranslations.incrementAndGet();
                    return text;
                }

                // Perform translation with retry logic
                String result = performTranslationWithRetry(cleanText, targetLanguage);

                // Cache successful translation
                if (!result.equals(cleanText) && translationCache.size() < plugin.getConfigManager().getCacheSize()) {
                    int estimatedTokens = estimateTokenCount(cleanText + result);
                    translationCache.put(cacheKey, new TranslationCacheEntry(result, estimatedTokens));
                    plugin.getConfigManager().debugLog("Cached OpenAI translation result");
                }

                successfulTranslations.incrementAndGet();
                return result;

            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected OpenAI translation error: " + e.getMessage());
                plugin.getConfigManager().debugLog("OpenAI translation error details: " + e.toString());
                failedTranslations.incrementAndGet();
                return text;
            }
        });
    }

    /**
     * Perform translation with retry logic
     */
    private String performTranslationWithRetry(String text, String targetLanguage) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    // Exponential backoff
                    int delay = BASE_RETRY_DELAY * (int) Math.pow(2, attempt - 1);
                    Thread.sleep(Math.min(delay, 5000)); // Max 5 seconds
                    plugin.getConfigManager().debugLog("Retrying OpenAI translation (attempt " + (attempt + 1) + ")");
                }

                return performTranslation(text, targetLanguage);

            } catch (SocketTimeoutException e) {
                lastException = e;
                plugin.getConfigManager().debugLog("OpenAI timeout on attempt " + (attempt + 1));
                if (attempt == MAX_RETRIES) {
                    plugin.getLogger().warning("OpenAI translation timeout after " + (attempt + 1) + " attempts");
                }
            } catch (IOException e) {
                lastException = e;
                // Don't retry on authentication errors
                if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                    throw e;
                }
                plugin.getConfigManager().debugLog("OpenAI error on attempt " + (attempt + 1) + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Translation interrupted", e);
            }
        }

        throw lastException != null ? lastException : new IOException("Translation failed after retries");
    }

    /**
     * error handling for different HTTP status codes
     */
    private void handleIOException(IOException e) {
        String message = e.getMessage();
        String errorKey = "general";

        if (message != null) {
            if (message.contains("401")) {
                plugin.getLogger().warning("OpenAI API authentication failed - check API key");
                errorKey = "auth";
            } else if (message.contains("403")) {
                plugin.getLogger().warning("OpenAI API access forbidden - check API key permissions");
                errorKey = "forbidden";
            } else if (message.contains("429")) {
                plugin.getLogger().warning("OpenAI API rate limit exceeded - consider upgrading plan");
                errorKey = "rate_limit";
            } else if (message.contains("quota") || message.contains("billing")) {
                plugin.getLogger().warning("OpenAI API quota exceeded - check billing settings");
                errorKey = "quota";
            } else if (message.contains("timeout")) {
                plugin.getLogger().warning("OpenAI API timeout - check network connection");
                errorKey = "timeout";
            } else {
                plugin.getLogger().warning("OpenAI API error: " + message);
            }
        } else {
            plugin.getLogger().warning("OpenAI translation failed: " + e.getClass().getSimpleName());
        }

        // Track error frequencies
        errorTracker.merge(errorKey, 1, Integer::sum);
    }

    private String createCacheKey(String text, String targetLanguage) {
        return "openai:" + targetLanguage + ":" + Integer.toHexString(text.toLowerCase().trim().hashCode());
    }

    /**
     * validation with better English detection and language support
     */
    private boolean isValidTranslationRequest(String text, String targetLanguage) {
        // Length validation
        if (!plugin.getConfigManager().isValidMessageLength(text)) {
            plugin.getConfigManager().debugLog("Message length validation failed");
            return false;
        }

        // Blacklist check
        if (plugin.getConfigManager().isBlacklisted(text)) {
            plugin.getConfigManager().debugLog("Message is blacklisted");
            return false;
        }

        // Message format validation
        if (!ChatUtil.isValidMessage(text)) {
            plugin.getConfigManager().debugLog("Message format validation failed");
            return false;
        }

        // English detection
        if (!plugin.getConfigManager().shouldTranslateEnglishText()) {
            try {
                if (LanguageUtil.appearsToBeEnglish(text) && "en".equals(targetLanguage.toLowerCase())) {
                    plugin.getConfigManager().debugLog("Skipping English-to-English translation");
                    return false;
                }
            } catch (Exception e) {
                plugin.getConfigManager().debugLog("Error in English detection: " + e.getMessage());
            }
        }

        // Language support validation
        if (!isLanguageSupported(targetLanguage)) {
            plugin.getConfigManager().debugLog("Unsupported target language: " + targetLanguage);
            return false;
        }

        return true;
    }

    /**
     * rate limiting with per-service tracking
     */
    private boolean checkRateLimit() {
        int rateLimit = plugin.getConfigManager().getRateLimitPerMinute();
        if (rateLimit <= 0) {
            return true;
        }

        String key = "openai:" + Thread.currentThread().getName();
        long currentTime = System.currentTimeMillis();
        long oneMinuteAgo = currentTime - 60000;

        // Clean old entries more efficiently
        rateLimitTracker.entrySet().removeIf(entry -> {
            try {
                String[] parts = entry.getKey().split(":", 3);
                if (parts.length >= 3) {
                    long timestamp = Long.parseLong(parts[2]);
                    return timestamp < oneMinuteAgo;
                }
                return true;
            } catch (Exception e) {
                return true;
            }
        });

        // Count recent requests for this thread/user
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

    /**
     * translation performance with configurable model selection
     */
    private String performTranslation(String text, String targetLanguage) throws IOException {
        String apiKey = getApiKey();
        String processedText = plugin.getConfigManager().truncateMessage(text);
        String languageName = LanguageUtil.getLanguageName(targetLanguage, getSupportedLanguages());

        URL url = new URL(OPENAI_API_URL);
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();

            // connection setup
            conn.setConnectTimeout(plugin.getConfigManager().getConnectionTimeout());
            conn.setReadTimeout(plugin.getConfigManager().getReadTimeout());
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("User-Agent", "TranslatePlus/2.0 (Minecraft Plugin)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setUseCaches(false);

            // Create request
            JsonObject requestBody = createOpenAIRequest(processedText, languageName, targetLanguage);

            // Send request with proper encoding
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input);
                os.flush();
            }

            apiCalls.incrementAndGet();

            //  response handling
            int responseCode = conn.getResponseCode();
            String responseBody = readResponse(conn, responseCode >= 400);

            if (responseCode != 200) {
                handleHttpError(responseCode, responseBody);
                throw new IOException("HTTP " + responseCode + ": " + responseBody);
            }

            return parseOpenAIResponse(responseBody, processedText);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Create request with better configuration
     */
    private JsonObject createOpenAIRequest(String text, String languageName, String targetLanguage) {
        JsonObject requestBody = new JsonObject();

        // Get model from config with fallback
        String model = plugin.getConfig().getString("service-settings.openai.model", "gpt-3.5-turbo");
        requestBody.addProperty("model", model);

        JsonArray messages = new JsonArray();

        //  system message with better context
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content",
                "You are a professional translator for a Minecraft chat system. " +
                        "Translate the following message accurately to " + languageName + " (" + targetLanguage + "). " +
                        "Requirements: " +
                        "1. Return ONLY the translated text, no explanations or quotes " +
                        "2. Preserve gaming terminology, player names, and Minecraft-specific terms " +
                        "3. Maintain the original tone and style (casual, formal, excited, etc.) " +
                        "4. If already in the target language, return unchanged " +
                        "5. Handle emojis and special characters appropriately " +
                        "6. Keep the message length reasonable for chat");

        messages.add(systemMessage);

        // User message
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", text);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        //  parameters from config
        int maxTokens = plugin.getConfig().getInt("service-settings.openai.max-tokens", 150);
        double temperature = plugin.getConfig().getDouble("service-settings.openai.temperature", 0.1);

        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("top_p", 0.9);
        requestBody.addProperty("frequency_penalty", 0.0);
        requestBody.addProperty("presence_penalty", 0.0);

        return requestBody;
    }

    /**
     * Better response reading with proper error handling
     */
    private String readResponse(HttpURLConnection conn, boolean isError) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                isError ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    /**
     *  HTTP error handling
     */
    private void handleHttpError(int responseCode, String responseBody) {
        switch (responseCode) {
            case 401:
                plugin.getLogger().warning("OpenAI API authentication failed - invalid API key");
                break;
            case 403:
                plugin.getLogger().warning("OpenAI API access forbidden - check API key permissions");
                break;
            case 429:
                plugin.getLogger().warning("OpenAI API rate limit exceeded");
                try {
                    JsonObject error = JsonParser.parseString(responseBody).getAsJsonObject();
                    if (error.has("error")) {
                        String message = error.getAsJsonObject("error").get("message").getAsString();
                        plugin.getConfigManager().debugLog("Rate limit details: " + message);
                    }
                } catch (Exception ignored) {}
                break;
            case 500:
                plugin.getLogger().warning("OpenAI API server error - try again later");
                break;
            case 503:
                plugin.getLogger().warning("OpenAI API temporarily unavailable");
                break;
            default:
                plugin.getLogger().warning("OpenAI API error " + responseCode + ": " + responseBody);
        }
    }

    /**
     * response parsing with better error handling
     */
    private String parseOpenAIResponse(String responseJson, String originalText) throws IOException {
        try {
            JsonObject jsonResponse = JsonParser.parseString(responseJson).getAsJsonObject();

            // Check for API errors
            if (jsonResponse.has("error")) {
                JsonObject error = jsonResponse.getAsJsonObject("error");
                String errorMessage = error.get("message").getAsString();
                String errorType = error.has("type") ? error.get("type").getAsString() : "unknown";
                throw new IOException("OpenAI API error (" + errorType + "): " + errorMessage);
            }

            // Extract translation
            if (jsonResponse.has("choices") && jsonResponse.get("choices").getAsJsonArray().size() > 0) {
                JsonObject choice = jsonResponse.get("choices").getAsJsonArray().get(0).getAsJsonObject();

                if (choice.has("message")) {
                    String translatedText = choice.get("message").getAsJsonObject()
                            .get("content").getAsString().trim();

                    // validation
                    if (translatedText.isEmpty()) {
                        plugin.getConfigManager().debugLog("Empty translation from OpenAI");
                        return originalText;
                    }

                    // Remove quotes if present (sometimes AI adds them)
                    if ((translatedText.startsWith("\"") && translatedText.endsWith("\"")) ||
                            (translatedText.startsWith("'") && translatedText.endsWith("'"))) {
                        translatedText = translatedText.substring(1, translatedText.length() - 1);
                    }

                    // Track usage for cost estimation
                    if (jsonResponse.has("usage")) {
                        JsonObject usage = jsonResponse.getAsJsonObject("usage");
                        int totalTokens = usage.get("total_tokens").getAsInt();
                        // Rough cost estimation (gpt-3.5-turbo: $0.002/1K tokens)
                        long costMicrodollars = (totalTokens * 2000L) / 1000; // 2000 microdollars per 1K tokens
                        totalCost.addAndGet(costMicrodollars);
                    }

                    plugin.getConfigManager().debugLog("OpenAI translation successful: " +
                            originalText.substring(0, Math.min(30, originalText.length())) + "... -> " +
                            translatedText.substring(0, Math.min(30, translatedText.length())) + "...");

                    return translatedText;
                }
            }

            plugin.getConfigManager().debugLog("Invalid OpenAI response structure");
            throw new IOException("Invalid response structure from OpenAI API");

        } catch (JsonSyntaxException e) {
            plugin.getConfigManager().debugLog("OpenAI JSON parsing error: " + e.getMessage());
            throw new IOException("Invalid JSON response from OpenAI API: " + e.getMessage());
        }
    }

    /**
     * Estimate token count for cost tracking
     */
    private int estimateTokenCount(String text) {
        // Rough estimation: 1 token ≈ 4 characters for English, varies for other languages
        return Math.max(1, text.length() / 4);
    }

    /**
     *  language support list
     */
    public Map<String, String> getSupportedLanguages() {
        Map<String, String> languages = new HashMap<>();

        // Major world languages
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

    /**
     *  cache management with expiration
     */
    public void clearCache() {
        int oldSize = translationCache.size() + rateLimitTracker.size();
        translationCache.clear();
        rateLimitTracker.clear();
        errorTracker.clear();
        plugin.getConfigManager().debugLog("OpenAI cache cleared - removed " + oldSize + " entries");
    }

    public int getCacheSize() {
        return translationCache.size();
    }

    /**
     *  maintenance with better metrics and cleanup
     */
    public void performMaintenance() {
        if (shutdownRequested) return;

        long currentTime = System.currentTimeMillis();

        // Run maintenance at most every 5 minutes
        if (currentTime - lastMaintenanceTime < 300000) {
            return;
        }

        lastMaintenanceTime = currentTime;
        plugin.getConfigManager().debugLog("Performing  OpenAI service maintenance");

        // Clean expired cache entries
        int removedEntries = 0;
        for (Map.Entry<String, TranslationCacheEntry> entry : translationCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                translationCache.remove(entry.getKey());
                removedEntries++;
            }
        }

        if (removedEntries > 0) {
            plugin.getConfigManager().debugLog("Removed " + removedEntries + " expired cache entries");
        }

        // Clean old rate limit entries (older than 1 hour)
        long oneHourAgo = currentTime - TimeUnit.HOURS.toMillis(1);
        rateLimitTracker.entrySet().removeIf(entry -> entry.getValue() < oneHourAgo);

        // Log detailed statistics
        if (plugin.getConfigManager().isDebugModeEnabled()) {
            plugin.getLogger().info("=== OpenAI Service Statistics ===");
            plugin.getLogger().info("Total translations: " + totalTranslations.get());
            plugin.getLogger().info("Successful: " + successfulTranslations.get());
            plugin.getLogger().info("Failed: " + failedTranslations.get());
            plugin.getLogger().info("Cache hits: " + cacheHits.get());
            plugin.getLogger().info("Cache size: " + translationCache.size());
            plugin.getLogger().info("API calls: " + apiCalls.get());
            plugin.getLogger().info("Estimated cost: $" + String.format("%.4f", totalCost.get() / 1_000_000.0));
            plugin.getLogger().info("Success rate: " + String.format("%.1f%%", getSuccessRate()));
            plugin.getLogger().info("Cache hit rate: " + String.format("%.1f%%", getCacheHitRate()));

            // Log error breakdown
            if (!errorTracker.isEmpty()) {
                plugin.getLogger().info("Error breakdown:");
                errorTracker.forEach((error, count) ->
                        plugin.getLogger().info("  " + error + ": " + count));
            }
        }

        // Cleanup if cache is too large
        int maxSize = plugin.getConfigManager().getCacheSize();
        if (translationCache.size() > maxSize * 1.2) { // Allow 20% overflow before cleanup
            // Remove oldest entries (simple LRU-like cleanup)
            translationCache.clear();
            plugin.getConfigManager().debugLog("OpenAI cache cleared due to size limit exceeded");
        }
    }

    public void shutdown() {
        shutdownRequested = true;
        clearCache();

        // Log final statistics
        if (plugin.getConfigManager().isDebugModeEnabled()) {
            plugin.getLogger().info("=== OpenAI Service Final Statistics ===");
            plugin.getLogger().info("Total translations processed: " + totalTranslations.get());
            plugin.getLogger().info("Success rate: " + String.format("%.1f%%", getSuccessRate()));
            plugin.getLogger().info("Total estimated cost: $" + String.format("%.4f", totalCost.get() / 1_000_000.0));
        }

        plugin.getConfigManager().debugLog(" OpenAI service shutdown completed");
    }

    /**
     * health check with more comprehensive status
     */
    public boolean isHealthy() {
        if (shutdownRequested) return false;

        // Check API key validity
        if (!validateApiKey()) return false;

        // Check cache size isn't excessive
        if (translationCache.size() > plugin.getConfigManager().getCacheSize() * 2) return false;

        // Check success rate
        double successRate = getSuccessRate();
        if (totalTranslations.get() > 10 && successRate < 70.0) return false;

        // Check for too many recent authentication errors
        Integer authErrors = errorTracker.get("auth");
        if (authErrors != null && authErrors > 5) return false;

        return true;
    }

    public double getSuccessRate() {
        long total = totalTranslations.get();
        if (total == 0) return 100.0;
        return (double) successfulTranslations.get() / total * 100.0;
    }

    /**
     * Calculate cache hit rate for performance monitoring
     */
    public double getCacheHitRate() {
        long total = totalTranslations.get();
        if (total == 0) return 0.0;
        return (double) cacheHits.get() / total * 100.0;
    }

    /**
     * Get estimated cost in dollars
     */
    public double getEstimatedCost() {
        return totalCost.get() / 1_000_000.0;
    }

    /**
     *  statistics with more detailed information
     */
    public Map<String, String> getCacheStats() {
        Map<String, String> stats = new HashMap<>();
        stats.put("service", "OpenAI Service");
        stats.put("cache_size", String.valueOf(translationCache.size()));
        stats.put("cache_limit", String.valueOf(plugin.getConfigManager().getCacheSize()));
        stats.put("total_translations", String.valueOf(totalTranslations.get()));
        stats.put("successful_translations", String.valueOf(successfulTranslations.get()));
        stats.put("failed_translations", String.valueOf(failedTranslations.get()));
        stats.put("cache_hits", String.valueOf(cacheHits.get()));
        stats.put("api_calls", String.valueOf(apiCalls.get()));
        stats.put("success_rate", String.format("%.1f%%", getSuccessRate()));
        stats.put("cache_hit_rate", String.format("%.1f%%", getCacheHitRate()));
        stats.put("estimated_cost", String.format("$%.4f", getEstimatedCost()));
        stats.put("health_status", isHealthy() ? "Healthy" : "Unhealthy");

        // Add current model being used
        String model = plugin.getConfig().getString("service-settings.openai.model", "gpt-3.5-turbo");
        stats.put("current_model", model);

        return stats;
    }

    /**
     * Get error statistics for monitoring
     */
    public Map<String, Integer> getErrorStats() {
        return new HashMap<>(errorTracker);
    }

    /**
     * Reset statistics (useful for testing or periodic resets)
     */
    public void resetStats() {
        totalTranslations.set(0);
        successfulTranslations.set(0);
        failedTranslations.set(0);
        cacheHits.set(0);
        apiCalls.set(0);
        totalCost.set(0);
        errorTracker.clear();
        plugin.getConfigManager().debugLog("OpenAI service statistics reset");
    }

    /**
     * Check if we're approaching API limits (helpful for monitoring)
     */
    public boolean isApproachingLimits() {
        // Check if we have high error rates for rate limiting
        Integer rateLimitErrors = errorTracker.get("rate_limit");
        if (rateLimitErrors != null && rateLimitErrors > 10) {
            return true;
        }

        // Check if quota errors are occurring
        Integer quotaErrors = errorTracker.get("quota");
        if (quotaErrors != null && quotaErrors > 0) {
            return true;
        }

        return false;
    }

    /**
     * Get recommended actions based on current status
     */
    public String getHealthRecommendation() {
        if (!validateApiKey()) {
            return "Invalid API key - please check your OpenAI API key configuration";
        }

        if (getSuccessRate() < 70.0 && totalTranslations.get() > 10) {
            return "Low success rate - check network connectivity and API quota";
        }

        if (isApproachingLimits()) {
            return "Approaching API limits - consider upgrading your OpenAI plan";
        }

        if (translationCache.size() > plugin.getConfigManager().getCacheSize() * 1.5) {
            return "Cache size is large - consider increasing cache-cleanup-interval";
        }

        Integer authErrors = errorTracker.get("auth");
        if (authErrors != null && authErrors > 2) {
            return "Multiple authentication errors - verify API key permissions";
        }

        return "Service is operating normally";
    }

    /**
     * Validate specific model availability
     */
    public boolean isModelSupported(String model) {
        // List of known supported models
        String[] supportedModels = {
                "gpt-3.5-turbo",
                "gpt-3.5-turbo-16k",
                "gpt-4",
                "gpt-4-32k",
                "gpt-4-turbo-preview",
                "gpt-4-1106-preview"
        };

        for (String supportedModel : supportedModels) {
            if (supportedModel.equals(model)) {
                return true;
            }
        }

        return false;
    }
}