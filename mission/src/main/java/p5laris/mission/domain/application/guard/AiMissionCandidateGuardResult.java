package p5laris.mission.domain.application.guard;

import java.util.Optional;

/**
 * AI 후보 후검증 결과다.
 */
public record AiMissionCandidateGuardResult(
        Optional<ValidatedAiMissionCandidate> candidate,
        Optional<AiMissionCandidateRejectionReason> rejectionReason
) {

    public static AiMissionCandidateGuardResult accepted(ValidatedAiMissionCandidate candidate) {
        return new AiMissionCandidateGuardResult(Optional.of(candidate), Optional.empty());
    }

    public static AiMissionCandidateGuardResult rejected(AiMissionCandidateRejectionReason reason) {
        return new AiMissionCandidateGuardResult(Optional.empty(), Optional.of(reason));
    }

    public boolean accepted() {
        return candidate.isPresent();
    }
}
