package p5laris.mission.domain.domain.enums;

/**
 * mission_outbox_events의 처리 상태다.
 */
public enum MissionOutboxEventStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED
}
