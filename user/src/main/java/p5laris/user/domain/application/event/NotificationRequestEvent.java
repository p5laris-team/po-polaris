package p5laris.user.domain.application.event;

public record NotificationRequestEvent(
    Long userId,
    String title,
    String body,
    String notificationType
) {}
