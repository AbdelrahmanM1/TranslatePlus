package dev.abdelrahman.translatePlus.managers;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.services.GoogleTranslateService;
import dev.abdelrahman.translatePlus.services.OpenAIService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages translation services and provides a unified interface
 * Automatically selects the appropriate service based on configuration
 */
public class TranslateServiceManager {

    private final TranslatePlus plugin;
    private GoogleTranslateService googleService;
    private OpenAIService openaiService;
    private String activeService;

    public TranslateServiceManager(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize translation services based on available API keys
     */
    public void initialize() {
        plugin.getConfigManager().debugLog("Initializing TranslateServiceManager...");

        String configuredService = plugin.getConfigManager().getTranslationService();
        String detectedService = detectBestService();

        // Use configured service if valid, otherwise use detected
        activeService = isServiceAvailable(configuredService) ? configuredService : detectedService;

        initializeActiveService();

        plugin.getLogger().info("Translation service initialized: " + activeService.toUpperCase());

        if (!activeService.equals(configuredService)) {
            plugin.getLogger().warning("Configured service '" + configuredService +
                    "' not available, using '" + activeService + "' instead");
        }
    }

    /**
     * Detect the best available service based on API key configuration
     */
    private String detectBestService() {
        boolean googleAvailable = isGoogleApiKeyValid();
        boolean openaiAvailable = isOpenAIApiKeyValid();

        plugin.getConfigManager().debugLog("Service availability - Google: " + googleAvailable + ", OpenAI: " + openaiAvailable);

        // Prefer the configured service, but fall back intelligently
        String preferred = plugin.getConfigManager().getTranslationService();

        if ("openai".equals(preferred) && openaiAvailable) {
            return "openai";
        } else if ("google".equals(preferred) && googleAvailable) {
            return "google";
        } else if (openaiAvailable) {
            return "openai";
        } else if (googleAvailable) {
            return "google";
        } else {
            // Default to google even if not configured (will show warnings)
            return "google";
        }
    }

    /**
     * Check if a service is available (has valid API key)
     */
    private boolean isServiceAvailable(String service) {
        if ("google".equals(service)) {
            return isGoogleApiKeyValid();
        } else if ("openai".equals(service)) {
            return isOpenAIApiKeyValid();
        }
        return false;
    }

    private boolean isGoogleApiKeyValid() {
        String key = plugin.getConfigManager().getGoogleApiKey();
        return key != null && !key.equals("PUT-YOUR-GOOGLE-KEY-HERE") && !key.trim().isEmpty();
    }

    private boolean isOpenAIApiKeyValid() {
        String key = plugin.getConfigManager().getOpenAIApiKey();
        return key != null && !key.equals("PUT-YOUR-OPENAI-KEY-HERE") &&
                key.startsWith("sk-") && key.length() > 20;
    }

    /**
     * Initialize the active service
     */
    private void initializeActiveService() {
        try {
            if ("openai".equals(activeService)) {
                openaiService = new OpenAIService(plugin);
                plugin.getConfigManager().debugLog("OpenAI service initialized");
            } else {
                googleService = new GoogleTranslateService(plugin);
                plugin.getConfigManager().debugLog("Google Translate service initialized");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize " + activeService + " service: " + e.getMessage());
            throw new RuntimeException("Translation service initialization failed", e);
        }
    }

    /**
     * Switch to a different service (if available)
     */
    public boolean switchService(String newService) {
        if (!isServiceAvailable(newService)) {
            plugin.getConfigManager().debugLog("Cannot switch to " + newService + " - API key not available");
            return false;
        }

        if (newService.equals(activeService)) {
            plugin.getConfigManager().debugLog("Service already active: " + newService);
            return true;
        }

        try {
            // Shutdown current service
            shutdown();

            // Switch to new service
            activeService = newService;
            initializeActiveService();

            plugin.getConfigManager().debugLog("Successfully switched to " + newService + " service");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to switch to " + newService + " service: " + e.getMessage());

            // Try to restore previous service
            try {
                initializeActiveService();
            } catch (Exception restoreError) {
                plugin.getLogger().severe("Failed to restore previous service: " + restoreError.getMessage());
            }

            return false;
        }
    }

    /**
     * Translate text using the active service
     */
    public CompletableFuture<String> translateText(String text, String targetLanguage) {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.translateText(text, targetLanguage);
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.translateText(text, targetLanguage);
        } else {
            plugin.getLogger().warning("No active translation service available!");
            return CompletableFuture.completedFuture(text);
        }
    }

    /**
     * Validate the active service's API key
     */
    public boolean validateApiKey() {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.validateApiKey();
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.validateApiKey();
        }
        return false;
    }

    /**
     * Get supported languages from the active service
     */
    public Map<String, String> getSupportedLanguages() {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.getSupportedLanguages();
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.getSupportedLanguages();
        }
        return Map.of(); // Empty map if no service available
    }

    /**
     * Check if a language is supported by the active service
     */
    public boolean isLanguageSupported(String languageCode) {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.isLanguageSupported(languageCode);
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.isLanguageSupported(languageCode);
        }
        return false;
    }

    /**
     * Clear cache for the active service
     */
    public void clearCache() {
        if ("openai".equals(activeService) && openaiService != null) {
            openaiService.clearCache();
        } else if ("google".equals(activeService) && googleService != null) {
            googleService.clearCache();
        }
    }

    /**
     * Get cache size from the active service
     */
    public int getCacheSize() {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.getCacheSize();
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.getCacheSize();
        }
        return 0;
    }

    /**
     * Perform maintenance on the active service
     */
    public void performMaintenance() {
        if ("openai".equals(activeService) && openaiService != null) {
            openaiService.performMaintenance();
        } else if ("google".equals(activeService) && googleService != null) {
            googleService.performMaintenance();
        }
    }

    /**
     * Check if the active service is healthy
     */
    public boolean isHealthy() {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.isHealthy();
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.isHealthy();
        }
        return false;
    }

    /**
     * Get success rate from the active service
     */
    public double getSuccessRate() {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.getSuccessRate();
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.getSuccessRate();
        }
        return 0.0;
    }

    /**
     * Get cache statistics from the active service
     */
    public Map<String, String> getCacheStats() {
        if ("openai".equals(activeService) && openaiService != null) {
            return openaiService.getCacheStats();
        } else if ("google".equals(activeService) && googleService != null) {
            return googleService.getCacheStats();
        }
        return Map.of();
    }

    /**
     * Get the name of the currently active service
     */
    public String getActiveServiceName() {
        return activeService != null ? activeService.toUpperCase() : "NONE";
    }

    /**
     * Get available services (those with valid API keys)
     */
    public String[] getAvailableServices() {
        return new String[]{
                isGoogleApiKeyValid() ? "google" : null,
                isOpenAIApiKeyValid() ? "openai" : null
        };
    }

    /**
     * Shutdown all services
     */
    public void shutdown() {
        plugin.getConfigManager().debugLog("Shutting down TranslateServiceManager...");

        if (googleService != null) {
            try {
                googleService.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Error shutting down Google service: " + e.getMessage());
            }
            googleService = null;
        }

        if (openaiService != null) {
            try {
                openaiService.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Error shutting down OpenAI service: " + e.getMessage());
            }
            openaiService = null;
        }

        plugin.getConfigManager().debugLog("TranslateServiceManager shutdown complete");
    }
    public Object getActiveService() {
        return activeService;
    }
    /**
     * Get detailed status information
     */
    public String getStatusInfo() {
        StringBuilder status = new StringBuilder();
        status.append("Active Service: ").append(getActiveServiceName()).append("\n");
        status.append("Google API Available: ").append(isGoogleApiKeyValid()).append("\n");
        status.append("OpenAI API Available: ").append(isOpenAIApiKeyValid()).append("\n");
        status.append("Service Healthy: ").append(isHealthy()).append("\n");
        status.append("Success Rate: ").append(String.format("%.1f%%", getSuccessRate())).append("\n");
        status.append("Cache Size: ").append(getCacheSize());
        return status.toString();
    }
}