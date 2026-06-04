package p5laris.character.domain.domain.enums;

import java.util.Locale;

public enum CharacterMood {
    IDLE("IDLE", "idle"),
    HAPPY("HAPPY", "happy"),
    SLEEPY("SLEEPY", "sleepy"),
    HUNGRY("HUNGRY", "hungry"),
    LOW_ENERGY("LOW_ENERGY", "lowEnergy"),
    LONELY("LONELY", "lonely");

    private final String assetType;
    private final String responseKey;

    CharacterMood(String assetType, String responseKey) {
        this.assetType = assetType;
        this.responseKey = responseKey;
    }

    public String assetType() {
        return assetType;
    }

    public String responseKey() {
        return responseKey;
    }

    public static CharacterMood fromAssetType(String assetType) {
        String normalized = assetType == null
                ? ""
                : assetType.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        for (CharacterMood mood : values()) {
            if (mood.assetType.equals(normalized)) {
                return mood;
            }
        }
        throw new IllegalArgumentException("Unknown character mood asset type: " + assetType);
    }
}
