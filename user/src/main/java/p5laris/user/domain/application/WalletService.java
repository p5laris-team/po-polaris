package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.WalletRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final StarPieceTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Wallet getMyWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));
    }

    @Transactional
    public StarPieceTransaction earnStarPiece(Long userId, int amount, String reason, String refType, Long refId, String idempotencyKey) {
        if (amount < 0) throw new UserException(UserErrorCode.EARN_AMOUNT_MUST_BE_POSITIVE);

        // 이미 처리된 요청인지 확인
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<StarPieceTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));

        // 보상 지급
        wallet.addStarPiece(amount);

        String keyToSave = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : null;

        // 별조각 거래내역에 추가
        StarPieceTransaction tx = StarPieceTransaction.builder()
                .userId(userId)
                .transactionType("EARN")
                .amount(amount)
                .balanceAfter(wallet.getStarPiece())
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .idempotencyKey(keyToSave)
                .build();
                
        return transactionRepository.save(tx);
    }

    // 별조각 차감
    @Transactional
    public StarPieceTransaction spendStarPiece(Long userId, int amount, String reason, String refType, Long refId, String idempotencyKey) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");

        // 1. 멱등성 검증 (Idempotency Check)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<StarPieceTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                // 이미 처리된 요청이면 재화 차감 없이 기존 성공 내역 즉시 반환 (Early Return)
                return existing.get();
            }
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));

        if (wallet.getStarPiece() < amount) {
            throw new UserException(UserErrorCode.STAR_PIECE_NOT_ENOUGH);
        }

        // 2. 정상적인 별조각 차감 로직 수행
        wallet.useStarPiece(amount);

        String keyToSave = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : null;

        // 별조각 거래내역에 추가
        StarPieceTransaction tx = StarPieceTransaction.builder()
                .userId(userId)
                .transactionType("SPEND")
                .amount(-amount)
                .balanceAfter(wallet.getStarPiece())
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .idempotencyKey(keyToSave)
                .build();
                
        return transactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public com.p5laris.proto.user.v1.GetTransactionsResponse getTransactions(com.p5laris.proto.user.v1.GetTransactionsRequest request) {
        Long userId = request.getUserId();
        String cursor = request.getCursor();
        int size = request.getSize() > 0 ? request.getSize() : 20;

        Long cursorId = parseCursor(cursor);

        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, size + 1);
        java.util.List<StarPieceTransaction> transactions = transactionRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursorId, pageRequest);

        boolean hasNext = false;
        java.util.List<StarPieceTransaction> resultList = transactions;
        if (transactions.size() > size) {
            hasNext = true;
            resultList = transactions.subList(0, size);
        }

        com.p5laris.proto.user.v1.GetTransactionsResponse.Builder responseBuilder =
                com.p5laris.proto.user.v1.GetTransactionsResponse.newBuilder();

        for (StarPieceTransaction tx : resultList) {
            String occurredAt = "";
            if (tx.getCreatedAt() != null) {
                occurredAt = tx.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString();
            }

            String description = "";
            if (tx.getReason() != null) {
                description = switch (tx.getReason()) {
                    case "ATTENDANCE_REWARD", "ATTENDANCE" -> "오늘 출석 보상";
                    case "MISSION_REWARD" -> "미션 완료 보상";
                    case "ITEM_PURCHASE" -> {
                        // DB 데이터 혹은 Context 상에서 더 상세한 아이템 정보가 명시되어 있다면 좋지만, MVP 수준에서는 API Spec 7.2의 "말랑 별빛 스킨 구매" 예시처럼
                        // basic한 아이템 설명 매핑이나 기본 설명을 내려준다.
                        yield "아이템 구매";
                    }
                    case "SHARE_REWARD" -> "공유 보상";
                    default -> tx.getReason();
                };
            }

            com.p5laris.proto.user.v1.Transaction protoTx = com.p5laris.proto.user.v1.Transaction.newBuilder()
                    .setId(tx.getId())
                    .setAmount(tx.getAmount())
                    .setBalanceAfter(tx.getBalanceAfter())
                    .setReason(tx.getReason() != null ? tx.getReason() : "")
                    .setDescription(description)
                    .setSourceType(tx.getRefType() != null ? tx.getRefType() : "")
                    .setSourceId(tx.getRefId() != null ? tx.getRefId() : 0L)
                    .setOccurredAt(occurredAt)
                    .build();

            responseBuilder.addItems(protoTx);
        }

        String nextCursor = "";
        if (hasNext && !resultList.isEmpty()) {
            nextCursor = encodeCursor(resultList.get(resultList.size() - 1).getId());
        }

        responseBuilder.setPageInfo(
                com.p5laris.proto.user.v1.TransactionPageInfo.newBuilder()
                        .setNextCursor(nextCursor)
                        .setHasNext(hasNext)
                        .setSize(size)
                        .build()
        );

        return responseBuilder.build();
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return Long.MAX_VALUE;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(cursor);
            String rawId = new String(decoded);
            return Long.parseLong(rawId);
        } catch (Exception e) {
            try {
                return Long.parseLong(cursor);
            } catch (NumberFormatException nfe) {
                return Long.MAX_VALUE;
            }
        }
    }

    private String encodeCursor(Long id) {
        if (id == null) return "";
        return java.util.Base64.getEncoder().encodeToString(String.valueOf(id).getBytes());
    }

    @Transactional(readOnly = true)
    public Optional<StarPieceTransaction> findTransactionByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }
}
