package p5laris.gateway.domain.item.infrastructure.grpc;

import com.p5laris.proto.item.v1.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.item.exception.ItemGatewayErrorCode;
import p5laris.gateway.domain.item.exception.ItemGatewayException;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

@Service
public class ItemGatewayService {

    @GrpcClient("item")
    private ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    // 상점 아이템 목록 조회
    public GetItemsResponse getItems(Long userId, String itemType, String cursor, int size) {
        try {
            return itemStub.getItems(
                    GetItemsRequest.newBuilder()
                            .setUserId(userId != null ? userId : 0L)
                            .setItemType(itemType != null ? itemType : "")
                            .setCursor(cursor != null ? cursor : "")
                            .setSize(size)
                            .build()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    public GetUserItemsResponse getUserItems(Long userId, String itemType, String cursor, int size) {
        try {
            return itemStub.getUserItems(
                    GetUserItemsRequest.newBuilder()
                            .setUserId(userId)
                            .setItemType(itemType != null ? itemType : "")
                            .setCursor(cursor != null ? cursor : "")
                            .setSize(size)
                            .build()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    public PurchaseItemResponse purchaseItem(Long userId, Long itemId, int quantity, String idempotencyKey) {
        try {
            return itemStub.purchaseItem(
                    PurchaseItemRequest.newBuilder()
                            .setUserId(userId)
                            .setItemId(itemId)
                            .setQuantity(quantity)
                            .setIdempotencyKey(idempotencyKey != null ? idempotencyKey : "")
                            .build()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    public java.util.Map<String, String> getSkinAssets(Long skinItemId, Long characterTypeId) {
        try {
            GetSkinAssetsResponse response = itemStub.getSkinAssets(
                    GetSkinAssetsRequest.newBuilder()
                            .setSkinItemId(skinItemId)
                            .setCharacterTypeId(characterTypeId)
                            .build()
            );
            return response.getAssetUrlsMap();
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    private BusinessException toGatewayException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();

        return switch (code) {
            case NOT_FOUND -> new ItemGatewayException(toNotFoundErrorCode(description));
            case ALREADY_EXISTS -> new ItemGatewayException(ItemGatewayErrorCode.ITEM_ALREADY_OWNED);
            case FAILED_PRECONDITION -> new ItemGatewayException(toFailedPreconditionErrorCode(description));
            case INVALID_ARGUMENT, UNAVAILABLE -> new ItemGatewayException(ItemGatewayErrorCode.ITEM_SERVICE_UNAVAILABLE);
            default -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        };
    }

    private ItemGatewayErrorCode toNotFoundErrorCode(String description) {
        if (contains(description, "USER_ITEM_NOT_FOUND") || contains(description, "ITEM-005")) {
            return ItemGatewayErrorCode.USER_ITEM_NOT_FOUND;
        }
        return ItemGatewayErrorCode.ITEM_NOT_FOUND;
    }

    private ItemGatewayErrorCode toFailedPreconditionErrorCode(String description) {
        if (contains(description, "STAR_PIECE_NOT_ENOUGH") || contains(description, "ITEM-003")) {
            return ItemGatewayErrorCode.STAR_PIECE_NOT_ENOUGH;
        }
        if (contains(description, "ITEM_QUANTITY_NOT_ENOUGH") || contains(description, "ITEM-006")) {
            return ItemGatewayErrorCode.ITEM_QUANTITY_NOT_ENOUGH;
        }
        return ItemGatewayErrorCode.ITEM_SERVICE_UNAVAILABLE;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }
}
