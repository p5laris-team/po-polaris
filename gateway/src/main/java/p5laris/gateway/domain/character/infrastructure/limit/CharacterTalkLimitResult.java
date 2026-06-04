package p5laris.gateway.domain.character.infrastructure.limit;

/**
 * 별친구 대화 일일 제한 확인 결과다.
 *
 * remainingCount는 Redis 장애처럼 정확히 계산할 수 없는 경우 null로 내려준다.
 */
public record CharacterTalkLimitResult(
        CharacterTalkLimitStatus talkStatus,
        int dailyLimit,
        Integer remainingCount,
        String resetAt
) {

    public static CharacterTalkLimitResult available(int dailyLimit, Integer remainingCount, String resetAt) {
        return new CharacterTalkLimitResult(CharacterTalkLimitStatus.AVAILABLE, dailyLimit, remainingCount, resetAt);
    }

    public static CharacterTalkLimitResult limitExceeded(int dailyLimit, String resetAt) {
        return new CharacterTalkLimitResult(CharacterTalkLimitStatus.LIMIT_EXCEEDED, dailyLimit, 0, resetAt);
    }

    public static CharacterTalkLimitResult temporarilyUnavailable(int dailyLimit, String resetAt) {
        return new CharacterTalkLimitResult(
                CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE,
                dailyLimit,
                null,
                resetAt
        );
    }

    public boolean available() {
        return talkStatus == CharacterTalkLimitStatus.AVAILABLE;
    }

    public boolean limitExceeded() {
        return talkStatus == CharacterTalkLimitStatus.LIMIT_EXCEEDED;
    }
}
