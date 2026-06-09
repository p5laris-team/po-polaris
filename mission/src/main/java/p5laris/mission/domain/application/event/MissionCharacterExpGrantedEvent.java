package p5laris.mission.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionCharacterExpGrantedEvent {

    private Long outboxId;
    private String idempotencyKey;
    private Long missionId;
    private Long characterId;
    private int expGained;
    private boolean levelUp;
    private boolean alreadyProcessed;
}
