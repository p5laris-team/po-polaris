package p5laris.item.domain.application;

import com.p5laris.proto.item.v1.GetSkinAssetsRequest;
import com.p5laris.proto.item.v1.GetSkinAssetsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.item.domain.domain.entity.Item;
import p5laris.item.domain.domain.entity.UserItem;
import p5laris.item.domain.domain.entity.UserItemPurchase;
import p5laris.item.domain.domain.repository.ItemRepository;
import p5laris.item.domain.domain.repository.UserItemRepository;
import p5laris.item.domain.domain.repository.UserItemUsageRepository;
import p5laris.item.domain.domain.repository.UserItemPurchaseRepository;
import p5laris.item.domain.exception.ItemException;
import p5laris.item.domain.infrastructure.config.ItemPurchaseWalletProperties;
import java.util.concurrent.TimeUnit;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private UserItemUsageRepository userItemUsageRepository;

    @Mock
    private UserItemPurchaseRepository userItemPurchaseRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.p5laris.proto.user.v1.WalletServiceGrpc.WalletServiceBlockingStub walletStub;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Mock
    private ItemPurchaseWalletProperties itemPurchaseWalletProperties;

    @InjectMocks
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        lenient().when(itemPurchaseWalletProperties.getDeadlineMs()).thenReturn(1000L);
        lenient().when(walletStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(walletStub);

        ReflectionTestUtils.setField(itemService, "cdnBaseUrl", "https://d24c6my56k1w5v.cloudfront.net");
        ReflectionTestUtils.setField(itemService, "walletStub", walletStub);
    }

    @Test
    @DisplayName("getSkinAssets - 스킨 아이템이 아닌 경우 예외 발생")
    void getSkinAssets_notSkinType_throwsException() {
        // given
        Long skinItemId = 1L;
        Long characterTypeId = 2L;

        Item nonSkinItem = Item.builder()
                .id(skinItemId)
                .name("일반 소비템")
                .itemType("CONSUMABLE")
                .build();

        when(itemRepository.findById(skinItemId)).thenReturn(Optional.of(nonSkinItem));

        GetSkinAssetsRequest request = GetSkinAssetsRequest.newBuilder()
                .setSkinItemId(skinItemId)
                .setCharacterTypeId(characterTypeId)
                .build();

        // when & then
        assertThatThrownBy(() -> itemService.getSkinAssets(request))
                .isInstanceOf(ItemException.class)
                .hasMessageContaining("유효하지 않은 아이템 타입입니다.");
    }

    @Test
    @DisplayName("getSkinAssets - 올바른 요청시 에셋 맵 정상 반환 및 low-energy 케이스 lowEnergy 매핑 확인")
    void getSkinAssets_validRequest_returnsAssetUrls() {
        // given
        Long skinItemId = 10L;
        Long characterTypeId = 2L; // 무무

        Item skinItem = Item.builder()
                .id(skinItemId)
                .name("말랑 별빛 스킨")
                .itemType("SKIN")
                .build();

        Item idleAsset = Item.builder()
                .id(11L)
                .name("말랑 별빛 스킨 - 무무 idle")
                .imageUrl("/assets/skins/starlight/equipped/mumu/core/skin-starlight-mumu-idle.png")
                .characterTypeId(characterTypeId)
                .build();

        Item lowEnergyAsset = Item.builder()
                .id(12L)
                .name("말랑 별빛 스킨 - 무무 low-energy")
                .imageUrl("/assets/skins/starlight/equipped/mumu/status/skin-starlight-mumu-low-energy.png")
                .characterTypeId(characterTypeId)
                .build();

        when(itemRepository.findById(skinItemId)).thenReturn(Optional.of(skinItem));
        when(itemRepository.findByNameStartingWithAndCharacterTypeId("말랑 별빛 스킨 - 무무 ", characterTypeId))
                .thenReturn(Arrays.asList(idleAsset, lowEnergyAsset));

        GetSkinAssetsRequest request = GetSkinAssetsRequest.newBuilder()
                .setSkinItemId(skinItemId)
                .setCharacterTypeId(characterTypeId)
                .build();

        // when
        GetSkinAssetsResponse response = itemService.getSkinAssets(request);

        // then
        assertThat(response.getAssetUrlsCount()).isEqualTo(2);
        assertThat(response.getAssetUrlsOrThrow("idle"))
                .isEqualTo("https://d24c6my56k1w5v.cloudfront.net/assets/skins/starlight/equipped/mumu/core/skin-starlight-mumu-idle.png");
        assertThat(response.getAssetUrlsOrThrow("lowEnergy"))
                .isEqualTo("https://d24c6my56k1w5v.cloudfront.net/assets/skins/starlight/equipped/mumu/status/skin-starlight-mumu-low-energy.png");
    }

    @Test
    @DisplayName("purchaseItem - 동일한 멱등키로 재요청 시 지갑 차감 없이 기존 구매 결과 반환")
    void purchaseItem_duplicateIdempotencyKey_returnsExistingPurchase() {
        // given
        Long userId = 1L;
        Long itemId = 10L;
        String idempotencyKey = "test-idempotency-key";

        com.p5laris.proto.item.v1.PurchaseItemRequest request = com.p5laris.proto.item.v1.PurchaseItemRequest.newBuilder()
                .setUserId(userId)
                .setItemId(itemId)
                .setQuantity(1)
                .setIdempotencyKey(idempotencyKey)
                .build();

        Item item = Item.builder()
                .id(itemId)
                .name("말랑 별빛 스킨")
                .itemType("SKIN")
                .price(60)
                .build();

        UserItem userItem = UserItem.builder()
                .id(100L)
                .userId(userId)
                .item(item)
                .quantity(1)
                .build();

        UserItemPurchase existingPurchase = UserItemPurchase.builder()
                .id(500L)
                .userId(userId)
                .userItem(userItem)
                .itemId(itemId)
                .quantity(1)
                .price(60)
                .starPiece(1000)
                .transactionId(12345L)
                .idempotencyKey(idempotencyKey)
                .build();

        when(userItemPurchaseRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingPurchase));

        // when
        com.p5laris.proto.item.v1.PurchaseItemResponse response = itemService.purchaseItem(request);

        // then
        assertThat(response.getPurchaseId()).isEqualTo(500L);
        assertThat(response.getItemId()).isEqualTo(itemId);
        assertThat(response.getName()).isEqualTo("말랑 별빛 스킨");
        assertThat(response.getQuantity()).isEqualTo(1);
        assertThat(response.getPrice()).isEqualTo(60);
        assertThat(response.getStarPiece()).isEqualTo(1000);
        assertThat(response.getTransactionId()).isEqualTo(12345L);

        // 지갑 API 및 DB 저장이 전혀 호출되지 않아야 함
        verify(userItemRepository, never()).save(any());
        verify(userItemPurchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("purchaseItem - 신규 구매 시 지갑 차감, 인벤토리 적재, 구매 이력 저장 후 올바른 purchaseId 반환")
    void purchaseItem_newPurchase_success() {
        // given
        Long userId = 1L;
        Long itemId = 10L;
        String idempotencyKey = "new-idempotency-key";

        com.p5laris.proto.item.v1.PurchaseItemRequest request = com.p5laris.proto.item.v1.PurchaseItemRequest.newBuilder()
                .setUserId(userId)
                .setItemId(itemId)
                .setQuantity(1)
                .setIdempotencyKey(idempotencyKey)
                .build();

        Item item = Item.builder()
                .id(itemId)
                .name("말랑 별빛 스킨")
                .itemType("SKIN")
                .price(60)
                .build();

        UserItem userItem = UserItem.builder()
                .id(100L)
                .userId(userId)
                .item(item)
                .quantity(1)
                .build();

        com.p5laris.proto.user.v1.SpendStarPieceResponse spendResponse = com.p5laris.proto.user.v1.SpendStarPieceResponse.newBuilder()
                .setStarPiece(940)
                .setTransactionId(12345L)
                .build();

        when(userItemPurchaseRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(itemRepository.findById(itemId))
                .thenReturn(Optional.of(item));
        when(userItemRepository.findByUserIdAndItemId(userId, itemId))
                .thenReturn(Optional.empty());
        
        when(walletStub.spendStarPiece(any(com.p5laris.proto.user.v1.SpendStarPieceRequest.class)))
                .thenReturn(spendResponse);
        
        when(userItemRepository.save(any(UserItem.class)))
                .thenReturn(userItem);

        UserItemPurchase savedPurchase = UserItemPurchase.builder()
                .id(500L)
                .userId(userId)
                .userItem(userItem)
                .itemId(itemId)
                .quantity(1)
                .price(60)
                .starPiece(940)
                .transactionId(12345L)
                .idempotencyKey(idempotencyKey)
                .build();

        when(userItemPurchaseRepository.save(any(UserItemPurchase.class)))
                .thenReturn(savedPurchase);
        when(userItemPurchaseRepository.findById(500L))
                .thenReturn(Optional.of(savedPurchase));

        // when
        com.p5laris.proto.item.v1.PurchaseItemResponse response = itemService.purchaseItem(request);

        // then
        assertThat(response.getPurchaseId()).isEqualTo(500L); // UserItemPurchase ID 여야 함
        assertThat(response.getItemId()).isEqualTo(itemId);
        assertThat(response.getName()).isEqualTo("말랑 별빛 스킨");
        assertThat(response.getQuantity()).isEqualTo(1);
        assertThat(response.getPrice()).isEqualTo(60);
        assertThat(response.getStarPiece()).isEqualTo(940);
        assertThat(response.getTransactionId()).isEqualTo(12345L);

        verify(walletStub, times(1)).spendStarPiece(any());
        verify(userItemRepository, times(2)).save(any());
        verify(userItemPurchaseRepository, times(2)).save(any());
    }
}
