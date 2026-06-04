package p5laris.mission.domain.infrastructure.grpc;

/**
 * mission 모듈이 user/wallet 모듈에서 받은 별조각 지급 결과다.
 *
 * starPiece는 wallet 반영 후 잔액이고, transactionId는 wallet 모듈의 star_piece_transactions id다.
 * 이미 보상 지급이 끝난 미션을 다시 조회할 때는 새 거래가 생기지 않으므로 transactionId가 null일 수 있다.
 */
public record WalletRewardResult(
        int starPiece,
        Long transactionId
) {
}
