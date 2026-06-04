package p5laris.notification.domain.application;

public record NotificationDeliveryDecision(
        boolean sendable,
        String skipCode,
        String skipMessage
) {

    public static NotificationDeliveryDecision allowed() {
        return new NotificationDeliveryDecision(true, null, null);
    }

    public static NotificationDeliveryDecision skipped(String skipCode, String skipMessage) {
        return new NotificationDeliveryDecision(false, skipCode, skipMessage);
    }
}
