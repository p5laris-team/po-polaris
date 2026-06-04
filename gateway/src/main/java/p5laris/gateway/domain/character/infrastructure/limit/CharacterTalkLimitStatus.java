package p5laris.gateway.domain.character.infrastructure.limit;

/**
 * 별친구 대화 가능 상태다.
 *
 * 프론트는 이 값을 기준으로 남은 횟수 표시와 제한 안내 UI를 분기한다.
 */
public enum CharacterTalkLimitStatus {
    AVAILABLE,
    LIMIT_EXCEEDED,
    TEMPORARILY_UNAVAILABLE
}
