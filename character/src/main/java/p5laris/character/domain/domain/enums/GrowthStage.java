package p5laris.character.domain.domain.enums;

/**
 * 캐릭터 성장 단계.
 *
 * 프론트 에셋과 성장 UI는 이 3단계를 기준으로 분기한다.
 */
public enum GrowthStage {
    BABY("처음 만난 별친구"),
    GROWING("조금 자란 별친구"),
    MATURE("반짝이는 동반자");

    private final String label;

    GrowthStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static GrowthStage fromLevel(int level) {
        if (level >= 3) {
            return MATURE;
        }
        if (level == 2) {
            return GROWING;
        }
        return BABY;
    }
}
