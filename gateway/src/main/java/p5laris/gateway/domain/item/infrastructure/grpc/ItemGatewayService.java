package p5laris.gateway.domain.item.infrastructure.grpc;

import com.p5laris.proto.item.v1.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class ItemGatewayService {

    @GrpcClient("item")
    private ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    // 상점 아이템 목록 조회
    public GetItemsResponse getItems(Long userId, String itemType, String cursor, int size) {
        return itemStub.getItems(
                GetItemsRequest.newBuilder()
                        .setUserId(userId != null ? userId : 0L)
                        .setItemType(itemType != null ? itemType : "")
                        .setCursor(cursor != null ? cursor : "")
                        .setSize(size)
                        .build()
        );
    }

    public GetUserItemsResponse getUserItems(Long userId, String itemType, String cursor, int size) {
        return itemStub.getUserItems(
                GetUserItemsRequest.newBuilder()
                        .setUserId(userId)
                        .setItemType(itemType != null ? itemType : "")
                        .setCursor(cursor != null ? cursor : "")
                        .setSize(size)
                        .build()
        );
    }

    public PurchaseItemResponse purchaseItem(Long userId, Long itemId, int quantity, String idempotencyKey) {
        return itemStub.purchaseItem(
                PurchaseItemRequest.newBuilder()
                        .setUserId(userId)
                        .setItemId(itemId)
                        .setQuantity(quantity)
                        .setIdempotencyKey(idempotencyKey != null ? idempotencyKey : "")
                        .build()
        );
    }

    public java.util.Map<String, String> getSkinAssets(Long skinItemId, Long characterTypeId) {
        GetSkinAssetsResponse response = itemStub.getSkinAssets(
                GetSkinAssetsRequest.newBuilder()
                        .setSkinItemId(skinItemId)
                        .setCharacterTypeId(characterTypeId)
                        .build()
        );
        return response.getAssetUrlsMap();
    }
}
