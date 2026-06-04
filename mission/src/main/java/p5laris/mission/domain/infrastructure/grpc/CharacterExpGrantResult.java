package p5laris.mission.domain.infrastructure.grpc;

public record CharacterExpGrantResult(
        Long characterId,
        int expGained,
        MissionCharacterGrowth beforeGrowth,
        MissionCharacterGrowth afterGrowth,
        boolean levelUp,
        boolean alreadyProcessed
) {
}
