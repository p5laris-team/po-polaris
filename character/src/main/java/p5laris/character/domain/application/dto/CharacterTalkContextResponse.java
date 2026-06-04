package p5laris.character.domain.application.dto;

import lombok.Builder;

import java.util.List;

/**
 * 별친구 대화 prompt에 넣을 캐릭터 context 응답이다.
 *
 * 사용자 대화 원문은 character 모듈에 저장하지 않고, 현재 상태/성장/해금 기억처럼
 * AI 답변 품질에 필요한 캐릭터 측 정보만 gateway와 ai 모듈에 전달한다.
 */
@Builder
public record CharacterTalkContextResponse(
        Long characterId,
        String characterTypeCode,
        String characterName,
        StateDetail hunger,
        StateDetail energy,
        StateDetail affection,
        CharacterGrowthResponse growth,
        List<Memory> memories,
        StoryProgress storyProgress
) {

    /**
     * prompt에는 숫자만 넣기보다 label/grade를 함께 내려서 AI가 상태를 단정하지 않고 부드럽게 해석하게 한다.
     */
    @Builder
    public record StateDetail(
            int value,
            String label,
            String grade
    ) {
    }

    /**
     * 이미 해금된 기억 조각 중 최근 N개만 전달한다.
     *
     * 전체 서사를 매번 넣으면 토큰 비용이 커지고, 아직 해금되지 않은 스포일러가 노출될 수 있다.
     */
    @Builder
    public record Memory(
            String memoryKey,
            String title,
            String storyText,
            String fragmentType,
            int unlockedLevel,
            String unlockedAt
    ) {
    }

    /**
     * 프론트가 성장 서사 진행률과 다음 해금 힌트를 바로 표시할 수 있게 내려주는 요약 정보다.
     *
     * 아직 해금되지 않은 storyText는 내려주지 않아 스포일러를 막는다.
     */
    @Builder
    public record StoryProgress(
            int unlockedMemoryCount,
            int totalMemoryCount,
            boolean allUnlocked,
            UnlockHint nextMemoryHint
    ) {
    }

    /**
     * 다음 기억 조각이 언제 열릴지 안내하는 최소 힌트다.
     */
    @Builder
    public record UnlockHint(
            int requiredLevel,
            String hintMessage
    ) {
    }
}
