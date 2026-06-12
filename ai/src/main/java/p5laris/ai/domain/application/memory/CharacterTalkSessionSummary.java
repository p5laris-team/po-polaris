package p5laris.ai.domain.application.memory;

/**
 * 별친구 대화 세션을 장기 기억용 요약과 사용자 노출용 일기 문구로 나눈 결과다.
 */
public record CharacterTalkSessionSummary(
        String contextSummary,
        String diaryText
) {

    public boolean isBlank() {
        return isBlank(contextSummary) && isBlank(diaryText);
    }

    public String resolvedContextSummary() {
        return isBlank(contextSummary) ? diaryText : contextSummary;
    }

    public String resolvedDiaryText() {
        return isBlank(diaryText) ? contextSummary : diaryText;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
