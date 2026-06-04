package p5laris.gateway.domain.item.api.dto;

import java.util.List;

public class ItemDto {

    public record ShopItem(
        Long id,
        String name,
        String description,
        String itemType,
        Long characterTypeId,
        String effectType,
        int price,
        String imageUrl,
        boolean owned
    ) {}

    public record UserItem(
        Long userItemId,
        Long itemId,
        String name,
        String itemType,
        Long characterTypeId,
        String effectType,
        int quantity,
        String imageUrl
    ) {}

    public record PageInfo(
        String nextCursor,
        boolean hasNext,
        int size
    ) {}

    public record GetItemsResponse(
        List<ShopItem> items,
        PageInfo pageInfo
    ) {}

    public record GetUserItemsResponse(
        List<UserItem> items,
        PageInfo pageInfo
    ) {}

    public record PurchaseRequest(
        Long itemId,
        int quantity,
        String idempotencyKey
    ) {}

    public record PurchaseResponse(
        Long purchaseId,
        Long itemId,
        String name,
        int quantity,
        int price,
        WalletSummary wallet,
        Long transactionId
    ) {
        public record WalletSummary(
            int starPiece
        ) {}
    }
}
