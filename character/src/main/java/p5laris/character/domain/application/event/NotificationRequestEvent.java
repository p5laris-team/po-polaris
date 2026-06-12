package p5laris.character.domain.application.event;

public record NotificationRequestEvent(
        Long userId,
        String title,
        String body,
        String notificationType,
        String targetType,
        Long targetId
) {
}
