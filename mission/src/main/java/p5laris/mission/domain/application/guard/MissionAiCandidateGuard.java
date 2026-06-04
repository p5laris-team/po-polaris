package p5laris.mission.domain.application.guard;

import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.diversity.MissionActionFamily;
import p5laris.mission.domain.application.diversity.MissionActionFamilyClassifier;
import p5laris.mission.domain.application.diversity.MissionDiversitySnapshot;
import p5laris.mission.domain.application.time.MissionTimePolicy;
import p5laris.mission.domain.application.time.MissionTimeSlot;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 미션 후보가 mission 서버 정책을 지키는지 최종 확인한다.
 *
 * 프롬프트는 품질을 높이는 1차 장치이고, 이 guard는 로그/통계 오염을 막는 마지막 방어선이다.
 */
@Component
public class MissionAiCandidateGuard {

    private static final int TITLE_MAX_LENGTH = 40;
    private static final int DESCRIPTION_MAX_LENGTH = 120;
    private static final int CHARACTER_MESSAGE_MAX_LENGTH = 160;
    private static final int COMPLETION_QUESTION_MAX_LENGTH = 100;
    private static final int COMPLETION_RESPONSE_MAX_LENGTH = 160;
    private static final Pattern CHALLENGE_VOLUME_PATTERN = Pattern.compile(
            ".*(5\\s*~\\s*10분|5분|6분|7분|8분|9분|10분|2\\s*~\\s*3세트|2세트|3세트|10회|12회|15회|라운드).*"
    );
    private static final List<String> PROHIBITED_EXPRESSIONS = List.of(
            "자해",
            "굶",
            "굶기",
            "술",
            "담배",
            "벌 받을",
            "안 하면",
            "무조건 참",
            "밤새",
            "무리해서"
    );

    public AiMissionCandidateGuardResult validate(AiMissionCandidateGuardRequest request) {
        if (request == null) {
            return reject(AiMissionCandidateRejectionReason.INVALID_CATEGORY_OR_DIFFICULTY);
        }

        AiMissionTextResult candidate = request.candidate();
        if (candidate == null || candidate.fallbackUsed()) {
            return reject(AiMissionCandidateRejectionReason.AI_FALLBACK_USED);
        }

        Optional<MissionCategoryType> category = toEnum(candidate.category(), MissionCategoryType.class);
        Optional<MissionDifficultyType> difficulty = toEnum(candidate.difficulty(), MissionDifficultyType.class);
        if (category.isEmpty() || difficulty.isEmpty()) {
            return reject(AiMissionCandidateRejectionReason.INVALID_CATEGORY_OR_DIFFICULTY);
        }

        MissionTimeSlot timeSlot = toTimeSlot(request.offeredAt());
        Optional<AiMissionCandidateRejectionReason> categoryRejectionReason = validateCategory(
                request.fallbackCategory(),
                category.get(),
                timeSlot
        );
        if (categoryRejectionReason.isPresent()) {
            return reject(categoryRejectionReason.get());
        }

        Optional<AiMissionCandidateRejectionReason> difficultyRejectionReason = validateDifficulty(
                difficulty.get(),
                request.challengeAlreadyUsedToday()
        );
        if (difficultyRejectionReason.isPresent()) {
            return reject(difficultyRejectionReason.get());
        }

        String title = normalizeText(candidate.title(), TITLE_MAX_LENGTH);
        String description = normalizeText(candidate.description(), DESCRIPTION_MAX_LENGTH);
        String characterMessage = normalizeText(candidate.characterMessage(), CHARACTER_MESSAGE_MAX_LENGTH);
        String completionQuestion = normalizeText(candidate.completionQuestion(), COMPLETION_QUESTION_MAX_LENGTH);
        String completionCharacterResponse = normalizeText(
                candidate.completionCharacterResponse(),
                COMPLETION_RESPONSE_MAX_LENGTH
        );
        if (title == null
                || description == null
                || characterMessage == null
                || completionQuestion == null
                || completionCharacterResponse == null) {
            return reject(AiMissionCandidateRejectionReason.INVALID_TEXT_LENGTH);
        }

        if (containsCharacterInterpretation(title, description)) {
            return reject(AiMissionCandidateRejectionReason.CHARACTER_TONE_IN_TITLE);
        }
        if (containsProhibitedExpression(title, description, characterMessage, completionQuestion, completionCharacterResponse)) {
            return reject(AiMissionCandidateRejectionReason.PROHIBITED_EXPRESSION);
        }
        if (containsBlockedKeyword(timeSlot, title, description, characterMessage, completionQuestion, completionCharacterResponse)) {
            return reject(AiMissionCandidateRejectionReason.BLOCKED_KEYWORD);
        }
        Optional<AiMissionCandidateRejectionReason> diversityRejectionReason = validateDiversity(
                request.missionId(),
                title,
                description,
                request.todayMissions()
        );
        if (diversityRejectionReason.isPresent()) {
            return reject(diversityRejectionReason.get());
        }
        if (difficulty.get() == MissionDifficultyType.CHALLENGE && !hasChallengeVolume(description)) {
            return reject(AiMissionCandidateRejectionReason.INVALID_CHALLENGE_VOLUME);
        }

        return AiMissionCandidateGuardResult.accepted(new ValidatedAiMissionCandidate(
                candidate.aiGenerationId(),
                title,
                description,
                characterMessage,
                completionQuestion,
                completionCharacterResponse,
                category.get(),
                difficulty.get()
        ));
    }

    private Optional<AiMissionCandidateRejectionReason> validateCategory(
            MissionCategoryType fallbackCategory,
            MissionCategoryType generatedCategory,
            MissionTimeSlot timeSlot
    ) {
        Set<MissionCategoryType> blockedCategories = MissionTimePolicy.blockedCategories(timeSlot);
        if (blockedCategories.contains(generatedCategory)) {
            return Optional.of(AiMissionCandidateRejectionReason.BLOCKED_CATEGORY);
        }

        boolean fallbackCategoryBlocked = fallbackCategory != null && blockedCategories.contains(fallbackCategory);
        if (!fallbackCategoryBlocked && generatedCategory != fallbackCategory) {
            return Optional.of(AiMissionCandidateRejectionReason.CATEGORY_CHANGED_WITHOUT_POLICY_REASON);
        }
        return Optional.empty();
    }

    private Optional<AiMissionCandidateRejectionReason> validateDifficulty(
            MissionDifficultyType generatedDifficulty,
            boolean challengeAlreadyUsedToday
    ) {
        if (generatedDifficulty == MissionDifficultyType.CHALLENGE && challengeAlreadyUsedToday) {
            return Optional.of(AiMissionCandidateRejectionReason.CHALLENGE_LIMIT_EXCEEDED);
        }

        Set<MissionDifficultyType> allowedDifficulties = challengeAlreadyUsedToday
                ? EnumSet.of(MissionDifficultyType.EASY, MissionDifficultyType.NORMAL)
                : EnumSet.allOf(MissionDifficultyType.class);
        if (!allowedDifficulties.contains(generatedDifficulty)) {
            return Optional.of(AiMissionCandidateRejectionReason.DIFFICULTY_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    private Optional<AiMissionCandidateRejectionReason> validateDiversity(
            Long currentMissionId,
            String title,
            String description,
            List<MissionDiversitySnapshot> todayMissions
    ) {
        if (todayMissions == null || todayMissions.isEmpty()) {
            return Optional.empty();
        }

        String candidateTitle = normalizeForComparison(title);
        MissionActionFamily candidateActionFamily = MissionActionFamilyClassifier.classify(title, description);

        for (MissionDiversitySnapshot mission : todayMissions) {
            if (mission == null || mission.missionId() != null && mission.missionId().equals(currentMissionId)) {
                continue;
            }
            String existingTitle = normalizeForComparison(mission.title());
            if (!candidateTitle.isBlank() && candidateTitle.equals(existingTitle)) {
                return Optional.of(AiMissionCandidateRejectionReason.DUPLICATE_TITLE);
            }
            if (candidateActionFamily != MissionActionFamily.UNKNOWN
                    && candidateActionFamily == mission.actionFamily()) {
                return Optional.of(AiMissionCandidateRejectionReason.DUPLICATE_ACTION_FAMILY);
            }
        }

        return Optional.empty();
    }

    private boolean hasChallengeVolume(String description) {
        return description != null && CHALLENGE_VOLUME_PATTERN.matcher(description).matches();
    }

    private boolean containsCharacterInterpretation(String... values) {
        for (String value : values) {
            if (value.contains("(해석:") || startsWithMumuUtterance(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean startsWithMumuUtterance(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("무우")
                || trimmed.startsWith("무무")
                || trimmed.startsWith("무...")
                || trimmed.startsWith("무…")
                || trimmed.startsWith("무?")
                || trimmed.startsWith("무!");
    }

    private boolean containsProhibitedExpression(String... values) {
        for (String value : values) {
            for (String expression : PROHIBITED_EXPRESSIONS) {
                if (value.contains(expression)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsBlockedKeyword(MissionTimeSlot timeSlot, String... values) {
        List<String> blockedKeywords = MissionTimePolicy.blockedMissionKeywords(timeSlot);
        if (blockedKeywords.isEmpty()) {
            return false;
        }

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            for (String keyword : blockedKeywords) {
                if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeForComparison(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private <T extends Enum<T>> Optional<T> toEnum(String value, Class<T> enumType) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            return null;
        }
        return normalized;
    }

    private MissionTimeSlot toTimeSlot(LocalDateTime offeredAt) {
        LocalDateTime baseTime = offeredAt == null ? LocalDateTime.now() : offeredAt;
        return MissionTimeSlot.from(baseTime);
    }

    private AiMissionCandidateGuardResult reject(AiMissionCandidateRejectionReason reason) {
        return AiMissionCandidateGuardResult.rejected(reason);
    }
}
