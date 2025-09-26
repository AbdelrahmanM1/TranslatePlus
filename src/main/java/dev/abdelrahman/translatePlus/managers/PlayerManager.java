package dev.abdelrahman.translatePlus.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {

    private final Map<UUID, String> playerLanguages;

    public PlayerManager() {
        this.playerLanguages = new HashMap<>();
    }

    public void enableTranslation(UUID playerId, String targetLanguage) {
        playerLanguages.put(playerId, targetLanguage.toLowerCase());
    }

    public void disableTranslation(UUID playerId) {
        playerLanguages.remove(playerId);
    }

    public boolean isTranslationEnabled(UUID playerId) {
        return playerLanguages.containsKey(playerId);
    }

    public String getPlayerLanguage(UUID playerId) {
        return playerLanguages.get(playerId);
    }

    public Map<UUID, String> getAllPlayers() {
        return new HashMap<>(playerLanguages);
    }

    public void clearAll() {
        playerLanguages.clear();
    }

    public void removePlayer(UUID playerId) {
        playerLanguages.remove(playerId);
    }

    public int getActivePlayersCount() {
        return playerLanguages.size();
    }
}