package p5laris.mission.domain.application.personalization;

/**
 * RAG 검색 query embedding을 만들 때 사용하는 미션 후보 스냅샷이다.
 */
public record MissionRagQuery(
        Long userId,
        Long missionTemplateId,
        String title,
        String description,
        String category,
        String difficulty
) {

    public String toEmbeddingText() {
        return String.join("\n",
                "미션 제목: " + nullToEmpty(title),
                "미션 설명: " + nullToEmpty(description),
                "카테고리: " + nullToEmpty(category),
                "난이도: " + nullToEmpty(difficulty)
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
