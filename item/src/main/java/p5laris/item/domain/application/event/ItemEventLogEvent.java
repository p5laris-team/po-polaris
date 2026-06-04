package p5laris.item.domain.application.event;

import p5laris.item.domain.domain.entity.Item;
import p5laris.item.domain.domain.entity.UserItem;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record ItemEventLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> metadata,
        OffsetDateTime occurredAt
) {
    // 아이템 구매 이벤트
    public static ItemEventLogEvent itemPurchased(
            Long userId,
            UserItem userItem,
            Item item,
            int quantity,
            int totalPrice,
            Long transactionId,
            int walletStarPieceAfter
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("purchaseId", userItem.getId());
        metadata.put("itemId", item.getId());
        metadata.put("itemName", item.getName());
        metadata.put("itemType", item.getItemType());
        metadata.put("quantity", quantity);
        metadata.put("totalPrice", totalPrice);
        metadata.put("transactionId", transactionId);
        metadata.put("walletStarPieceAfter", walletStarPieceAfter);

        return new ItemEventLogEvent(
                "ITEM_PURCHASED",
                userId,
                "ITEM",
                item.getId(),
                metadata,
                OffsetDateTime.now()
        );
    }

    // 별조각 사용 이벤트
    public static ItemEventLogEvent starPieceSpent(
            Long userId,
            Item item,
            int totalPrice,
            Long transactionId,
            int walletStarPieceAfter,
            String idempotencyKey
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", "ITEM_PURCHASE");
        metadata.put("amount", totalPrice);
        metadata.put("balanceAfter", walletStarPieceAfter);
        metadata.put("transactionId", transactionId);
        metadata.put("idempotencyKey", idempotencyKey);

        return new ItemEventLogEvent(
                "STAR_PIECE_SPENT",
                userId,
                "ITEM",
                item.getId(),
                metadata,
                OffsetDateTime.now()
        );
    }
}
