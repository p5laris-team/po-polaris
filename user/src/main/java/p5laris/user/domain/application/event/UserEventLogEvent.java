package p5laris.user.domain.application.event;

import p5laris.user.domain.domain.entity.AttendanceRecord;
import p5laris.user.domain.domain.entity.OnboardingProfile;
import p5laris.user.domain.domain.entity.StarPieceTransaction;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record UserEventLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> metadata,
        OffsetDateTime occurredAt
) {
    // 신규 가입 이벤트
    public static UserEventLogEvent userSignedUp(Long userId, String provider, String role, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider);
        metadata.put("role", role);
        metadata.put("status", status);
        return new UserEventLogEvent("USER_SIGNED_UP", userId, "USER", userId, metadata, OffsetDateTime.now());
    }

    // 기존 사용자 로그인 이벤트
    public static UserEventLogEvent userLoggedIn(Long userId, String provider) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider);
        return new UserEventLogEvent("USER_LOGGED_IN", userId, "USER", userId, metadata, OffsetDateTime.now());
    }

    // 온보딩 설문지 중간 저장 이벤트
    public static UserEventLogEvent onboardingProfileSaved(OnboardingProfile profile) {
        Map<String, Object> metadata = onboardingMetadata(profile);
        metadata.put("completed", profile.isCompleted());
        return new UserEventLogEvent(
                "ONBOARDING_PROFILE_SAVED",
                profile.getUserId(),
                "ONBOARDING_PROFILE",
                profile.getId(),
                metadata,
                OffsetDateTime.now()
        );
    }

    // 온보딩 완료 이벤트
    public static UserEventLogEvent onboardingCompleted(OnboardingProfile profile) {
        return new UserEventLogEvent(
                "ONBOARDING_COMPLETED",
                profile.getUserId(),
                "ONBOARDING_PROFILE",
                profile.getId(),
                onboardingMetadata(profile),
                OffsetDateTime.now()
        );
    }

    // 출석 체크 이벤트
    public static UserEventLogEvent attendanceChecked(AttendanceRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attendanceDate", record.getAttendanceDate().toString());
        metadata.put("streakCount", record.getStreakCount());
        metadata.put("rewardStarPiece", record.getRewardStarPiece());
        return new UserEventLogEvent(
                "ATTENDANCE_CHECKED",
                record.getUserId(),
                "ATTENDANCE_RECORD",
                record.getId(),
                metadata,
                OffsetDateTime.now()
        );
    }

    // 보상 수령 이벤트
    public static UserEventLogEvent starPieceEarned(AttendanceRecord record, StarPieceTransaction transaction) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", transaction.getReason());
        metadata.put("amount", transaction.getAmount());
        metadata.put("balanceAfter", transaction.getBalanceAfter());
        metadata.put("transactionId", transaction.getId());
        return new UserEventLogEvent(
                "STAR_PIECE_EARNED",
                record.getUserId(),
                "ATTENDANCE_RECORD",
                record.getId(),
                metadata,
                OffsetDateTime.now()
        );
    }

    private static Map<String, Object> onboardingMetadata(OnboardingProfile profile) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("livingType", profile.getLivingType());
        metadata.put("wakeUpTime", profile.getWakeUpTime());
        metadata.put("sleepTime", profile.getSleepTime());
        metadata.put("preferredMissionTime", profile.getPreferredMissionTime());
        metadata.put("routineGoal", profile.getRoutineGoal());
        metadata.put("activityPreference", profile.getActivityPreference());
        metadata.put("missionIntensity", profile.getMissionIntensity());
        metadata.put("onboardingVersion", profile.getOnboardingVersion());
        metadata.put("routineGoals", profile.getRoutineGoalsJson());
        metadata.put("preferredTimeSlots", profile.getPreferredTimeSlotsJson());
        metadata.put("missionPlaceContexts", profile.getMissionPlaceContextsJson());
        metadata.put("avoidedMissionTags", profile.getAvoidedMissionTagsJson());
        return metadata;
    }
}
