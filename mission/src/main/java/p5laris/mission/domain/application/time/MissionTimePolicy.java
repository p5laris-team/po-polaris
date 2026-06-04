package p5laris.mission.domain.application.time;

import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.domain.enums.MissionCategoryType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 시간대별 미션 추천/차단 정책을 한 곳에서 관리한다.
 *
 * AI 프롬프트, seed fallback 선택, AI 후보 최종 검증이 같은 정책을 보게 해서
 * 밤/새벽에 햇빛 미션처럼 맥락이 맞지 않는 후보가 확정되지 않게 한다.
 */
public final class MissionTimePolicy {

    private static final List<String> NIGHT_LIGHT_KEYWORDS = List.of(
            "햇빛",
            "햇살",
            "햇볕",
            "아침 햇살",
            "낮 산책",
            "sunlight",
            "daylight"
    );
    private static final List<String> LATE_NIGHT_ACTIVITY_KEYWORDS = List.of(
            "연락",
            "메시지",
            "문자",
            "전화",
            "카톡",
            "dm",
            "점프",
            "뛰기",
            "뛰어",
            "달리기",
            "스쿼트",
            "버피",
            "대청소",
            "청소기",
            "방 청소",
            "책상 정리"
    );

    private MissionTimePolicy() {
    }

    public static Map<String, Object> toContext(MissionTimeSlot timeSlot) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("currentTimeSlot", timeSlot.name());
        context.put("recommendedCategories", categoryNames(recommendedCategories(timeSlot)));
        context.put("blockedCategories", categoryNames(blockedCategories(timeSlot)));
        context.put("recommendedMissionTraits", recommendedMissionTraits(timeSlot));
        context.put("blockedMissionKeywords", blockedMissionKeywords(timeSlot));
        return context;
    }

    public static boolean isTemplateAllowed(MissionTimeSlot timeSlot, MissionTemplate template) {
        if (template == null) {
            return false;
        }
        return isCandidateAllowed(
                timeSlot,
                template.getCategory(),
                template.getBaseTitle(),
                template.getBaseDescription(),
                template.getFallbackCharacterMessage(),
                template.getFallbackQuestion(),
                template.getFallbackCompletionResponse()
        );
    }

    public static boolean isCandidateAllowed(
            MissionTimeSlot timeSlot,
            MissionCategoryType category,
            String... texts
    ) {
        if (timeSlot == null) {
            return true;
        }
        if (category != null && blockedCategories(timeSlot).contains(category)) {
            return false;
        }
        return !containsBlockedKeyword(timeSlot, texts);
    }

    public static Set<MissionCategoryType> blockedCategories(MissionTimeSlot timeSlot) {
        return switch (timeSlot) {
            case LATE_NIGHT -> EnumSet.of(MissionCategoryType.OUTDOOR_LIGHT, MissionCategoryType.SOCIAL_LIGHT);
            case NIGHT -> EnumSet.of(MissionCategoryType.OUTDOOR_LIGHT);
            case MORNING, AFTERNOON, EVENING -> EnumSet.noneOf(MissionCategoryType.class);
        };
    }

    public static List<MissionCategoryType> recommendedCategories(MissionTimeSlot timeSlot) {
        return switch (timeSlot) {
            case LATE_NIGHT -> List.of(
                    MissionCategoryType.REST_RECOVERY,
                    MissionCategoryType.MIND_RECORD,
                    MissionCategoryType.BASIC_ROUTINE
            );
            case NIGHT -> List.of(
                    MissionCategoryType.REST_RECOVERY,
                    MissionCategoryType.MIND_RECORD,
                    MissionCategoryType.BASIC_ROUTINE,
                    MissionCategoryType.SPACE_RESET
            );
            case MORNING -> List.of(
                    MissionCategoryType.BASIC_ROUTINE,
                    MissionCategoryType.BODY_CARE,
                    MissionCategoryType.OUTDOOR_LIGHT,
                    MissionCategoryType.SPACE_RESET
            );
            case AFTERNOON -> List.of(
                    MissionCategoryType.BODY_CARE,
                    MissionCategoryType.SPACE_RESET,
                    MissionCategoryType.MIND_RECORD,
                    MissionCategoryType.SOCIAL_LIGHT
            );
            case EVENING -> List.of(
                    MissionCategoryType.REST_RECOVERY,
                    MissionCategoryType.MIND_RECORD,
                    MissionCategoryType.SPACE_RESET,
                    MissionCategoryType.BODY_CARE
            );
        };
    }

    public static List<String> recommendedMissionTraits(MissionTimeSlot timeSlot) {
        return switch (timeSlot) {
            case LATE_NIGHT -> List.of(
                    "잠들기 전에도 부담 없는 1~3분 루틴",
                    "소리와 움직임이 작고 자극이 낮은 행동",
                    "침대, 책상, 현재 자리에서 끝낼 수 있는 기록이나 회복",
                    "내일의 나를 가볍게 준비하는 루틴"
            );
            case NIGHT -> List.of(
                    "하루를 천천히 닫는 회복 루틴",
                    "수면을 방해하지 않는 가벼운 정리나 기록",
                    "밝은 빛이나 야외 이동 없이 실내에서 할 수 있는 행동"
            );
            case MORNING -> List.of(
                    "몸과 하루를 천천히 깨우는 루틴",
                    "물, 가벼운 움직임, 밝은 빛처럼 시작감을 주는 행동",
                    "오늘의 첫 순서를 정하는 짧은 준비"
            );
            case AFTERNOON -> List.of(
                    "흐트러진 집중을 다시 잡는 루틴",
                    "책상 주변이나 몸 상태를 가볍게 리셋하는 행동",
                    "업무나 공부 중에도 할 수 있는 짧은 전환"
            );
            case EVENING -> List.of(
                    "오늘을 정리하고 몸의 긴장을 낮추는 루틴",
                    "집이나 실내에서 하기 쉬운 회복 행동",
                    "내일 부담을 줄이는 작은 준비"
            );
        };
    }

    public static List<String> blockedMissionKeywords(MissionTimeSlot timeSlot) {
        List<String> keywords = new ArrayList<>();
        if (timeSlot == MissionTimeSlot.NIGHT || timeSlot == MissionTimeSlot.LATE_NIGHT) {
            keywords.addAll(NIGHT_LIGHT_KEYWORDS);
        }
        if (timeSlot == MissionTimeSlot.LATE_NIGHT) {
            keywords.addAll(LATE_NIGHT_ACTIVITY_KEYWORDS);
        }
        return List.copyOf(keywords);
    }

    private static boolean containsBlockedKeyword(MissionTimeSlot timeSlot, String... texts) {
        List<String> blockedKeywords = blockedMissionKeywords(timeSlot);
        if (blockedKeywords.isEmpty() || texts == null) {
            return false;
        }

        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            for (String keyword : blockedKeywords) {
                if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> categoryNames(Iterable<MissionCategoryType> categories) {
        List<String> names = new ArrayList<>();
        for (MissionCategoryType category : categories) {
            names.add(category.name());
        }
        return names;
    }
}
