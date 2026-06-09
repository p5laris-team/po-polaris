package p5laris.mission.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionCharacterExpRequestedEvent {

    private Long outboxId;
    private Long missionId;
    private Long userId;
    private Long characterId;
    private String difficulty;
    private int expAmount;
    private String idempotencyKey;
}
