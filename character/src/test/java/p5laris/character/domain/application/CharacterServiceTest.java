package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import p5laris.character.domain.application.dto.CareActionResponse;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.entity.CharacterExpLog;
import p5laris.character.domain.domain.entity.CharacterStoryFragment;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.entity.UserCharacterStoryUnlock;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.enums.CharacterExpSourceType;
import p5laris.character.domain.domain.enums.CharacterStoryFragmentType;
import p5laris.character.domain.domain.enums.CharacterStoryTriggerType;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterCareLogRepository;
import p5laris.character.domain.domain.repository.CharacterExpLogRepository;
import p5laris.character.domain.domain.repository.CharacterStoryFragmentRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;
import p5laris.character.domain.domain.repository.UserCharacterStoryUnlockRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.config.CharacterItemGrpcProperties;

import p5laris.character.domain.domain.entity.CharacterAsset;
import p5laris.character.domain.domain.entity.CharacterType;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private CharacterTypeRepository characterTypeRepository;

    @Mock
    private CharacterAssetRepository characterAssetRepository;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private CharacterCareLogRepository characterCareLogRepository;

    @Mock
    private CharacterExpLogRepository characterExpLogRepository;

    @Mock
    private CharacterStoryFragmentRepository characterStoryFragmentRepository;

    @Mock
    private UserCharacterStoryUnlockRepository userCharacterStoryUnlockRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private com.p5laris.proto.item.v1.ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    @Mock
    private CharacterItemGrpcProperties itemGrpcProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CharacterService characterService;

    private UserCharacter character;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(characterService, "itemStub", itemStub);
        lenient().when(itemGrpcProperties.getDeadlineMs()).thenReturn(1000L);
        lenient().when(itemStub.withDeadlineAfter(1000L, TimeUnit.MILLISECONDS)).thenReturn(itemStub);
        lenient().when(s3StorageService.toPublicUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CharacterType characterType = CharacterType.builder()
                .code("NOVA")
                .name("Nova")
                .active(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(characterType, "id", 1L);

        character = UserCharacter.builder()
                .userId(1L)
                .characterType(characterType)
                .name("Nova")
                .level(1)
                .exp(0)
                .fullness(50)
                .energy(50)
                .affection(50)
                .active(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(character, "id", 1L);
    }

    @Test
    @DisplayName("캐릭터 상호작용 - 처음 보는 LORE 조각이면 해금 저장 후 반환")
    void interactWithCharacter_newLore_unlocksMemory() {
        CharacterStoryFragment common = storyFragment(
                10L,
                "NOVA",
                1,
                CharacterStoryFragmentType.COMMON,
                CharacterStoryTriggerType.TAP,
                "nova_lv1_common_001"
        );
        CharacterStoryFragment lore = storyFragment(
                11L,
                "NOVA",
                1,
                CharacterStoryFragmentType.LORE,
                CharacterStoryTriggerType.TAP,
                "nova_lv1_lore_001"
        );

        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterStoryFragmentRepository
                .findByActiveTrueAndCharacterTypeCodeInAndTriggerTypeAndMinLevelLessThanEqualOrderBySortOrderAscIdAsc(
                        anyList(),
                        eq(CharacterStoryTriggerType.TAP),
                        eq(1)
                ))
                .thenReturn(List.of(common, lore));
        when(userCharacterStoryUnlockRepository.findByUserIdAndUserCharacterIdAndStoryFragmentIdIn(
                eq(1L),
                eq(1L),
                anyCollection()
        )).thenReturn(List.of());

        var response = characterService.interactWithCharacter(1L, 1L, "TAP");

        assertEquals("LORE", response.fragmentType());
        assertEquals("NOVA", response.characterTypeCode());
        assertTrue(response.memoryUnlocked());
        assertFalse(response.alreadyUnlocked());
        assertNotNull(response.memory());
        assertEquals("nova_lv1_lore_001", response.memory().memoryKey());
        verify(userCharacterStoryUnlockRepository).save(any(UserCharacterStoryUnlock.class));
    }

    @Test
    @DisplayName("캐릭터 상호작용 - COMMON 조각은 대사로 반환하지만 해금 저장하지 않음")
    void interactWithCharacter_commonFragment_doesNotSaveUnlock() {
        CharacterStoryFragment common = storyFragment(
                10L,
                "NOVA",
                1,
                CharacterStoryFragmentType.COMMON,
                CharacterStoryTriggerType.TAP,
                "nova_lv1_common_001"
        );

        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterStoryFragmentRepository
                .findByActiveTrueAndCharacterTypeCodeInAndTriggerTypeAndMinLevelLessThanEqualOrderBySortOrderAscIdAsc(
                        anyList(),
                        eq(CharacterStoryTriggerType.TAP),
                        eq(1)
                ))
                .thenReturn(List.of(common));

        var response = characterService.interactWithCharacter(1L, 1L, "TAP");

        assertEquals("COMMON", response.fragmentType());
        assertFalse(response.memoryUnlocked());
        assertFalse(response.alreadyUnlocked());
        assertNull(response.memory());
        verifyNoInteractions(userCharacterStoryUnlockRepository);
    }

    @Test
    @DisplayName("캐릭터 상호작용 - 이미 해금한 LORE 조각은 중복 저장하지 않음")
    void interactWithCharacter_alreadyUnlockedLore_reusesMemory() {
        CharacterStoryFragment lore = storyFragment(
                11L,
                "NOVA",
                1,
                CharacterStoryFragmentType.LORE,
                CharacterStoryTriggerType.TAP,
                "nova_lv1_lore_001"
        );
        UserCharacterStoryUnlock unlock = UserCharacterStoryUnlock.builder()
                .userId(1L)
                .userCharacterId(1L)
                .storyFragmentId(11L)
                .memoryKey("nova_lv1_lore_001")
                .unlockedLevel(1)
                .build();

        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterStoryFragmentRepository
                .findByActiveTrueAndCharacterTypeCodeInAndTriggerTypeAndMinLevelLessThanEqualOrderBySortOrderAscIdAsc(
                        anyList(),
                        eq(CharacterStoryTriggerType.TAP),
                        eq(1)
                ))
                .thenReturn(List.of(lore));
        when(userCharacterStoryUnlockRepository.findByUserIdAndUserCharacterIdAndStoryFragmentIdIn(
                eq(1L),
                eq(1L),
                anyCollection()
        )).thenReturn(List.of(unlock));

        var response = characterService.interactWithCharacter(1L, 1L, "TAP");

        assertEquals("LORE", response.fragmentType());
        assertFalse(response.memoryUnlocked());
        assertTrue(response.alreadyUnlocked());
        assertNotNull(response.memory());
        verify(userCharacterStoryUnlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("캐릭터 상호작용 - 현재 레벨보다 높은 조각은 방어적으로 제외")
    void interactWithCharacter_higherLevelFragment_returnsFallback() {
        CharacterStoryFragment highLevelLore = storyFragment(
                20L,
                "NOVA",
                2,
                CharacterStoryFragmentType.LORE,
                CharacterStoryTriggerType.TAP,
                "nova_lv2_lore_001"
        );

        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterStoryFragmentRepository
                .findByActiveTrueAndCharacterTypeCodeInAndTriggerTypeAndMinLevelLessThanEqualOrderBySortOrderAscIdAsc(
                        anyList(),
                        eq(CharacterStoryTriggerType.TAP),
                        eq(1)
                ))
                .thenReturn(List.of(highLevelLore));

        var response = characterService.interactWithCharacter(1L, 1L, "TAP");

        assertEquals("COMMON", response.fragmentType());
        assertEquals("TAP", response.triggerType());
        assertNull(response.memory());
        verifyNoInteractions(userCharacterStoryUnlockRepository);
    }

    @Test
    @DisplayName("캐릭터 생성 - 생성 직후 초기 성장 정보 반환")
    void createCharacter_returnsInitialGrowth() {
        CharacterType characterType = character.getCharacterType();
        when(characterTypeRepository.findById(1L)).thenReturn(Optional.of(characterType));
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(userCharacterRepository.save(any(UserCharacter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = characterService.createCharacter(1L, 1L, "Nova");

        assertNotNull(response.growth());
        assertEquals(1, response.growth().level());
        assertEquals(0, response.growth().exp());
        assertEquals(200, response.growth().nextLevelExp());
        assertEquals("BABY", response.growth().growthStage());
        assertFalse(response.growth().maxLevel());
    }

    @Test
    @DisplayName("캐릭터 상태 조회 - 상태 응답에 성장 정보 포함")
    void getCharacterStatus_returnsGrowth() {
        org.springframework.test.util.ReflectionTestUtils.setField(character, "exp", 200);
        org.springframework.test.util.ReflectionTestUtils.setField(character, "level", 2);
        when(userCharacterRepository.findById(1L)).thenReturn(Optional.of(character));

        var response = characterService.getCharacterStatus(1L, 1L);

        assertNotNull(response.growth());
        assertEquals(2, response.growth().level());
        assertEquals(200, response.growth().exp());
        assertEquals("GROWING", response.growth().growthStage());
        assertEquals(0, response.growth().progressPercent());
    }

    @Test
    @DisplayName("별친구 대화 context 조회 - 상태, 성장, 최근 해금 기억을 반환")
    void getCharacterTalkContext_returnsStatesGrowthAndRecentMemories() {
        CharacterStoryFragment first = storyFragment(
                11L,
                "NOVA",
                1,
                CharacterStoryFragmentType.LORE,
                CharacterStoryTriggerType.TAP,
                "nova_lv1_lore_001"
        );
        CharacterStoryFragment second = storyFragment(
                12L,
                "NOVA",
                1,
                CharacterStoryFragmentType.EASTER_EGG,
                CharacterStoryTriggerType.TAP,
                "nova_lv1_easter_001"
        );
        UserCharacterStoryUnlock firstUnlock = UserCharacterStoryUnlock.builder()
                .userId(1L)
                .userCharacterId(1L)
                .storyFragmentId(11L)
                .memoryKey("nova_lv1_lore_001")
                .unlockedLevel(1)
                .build();
        UserCharacterStoryUnlock secondUnlock = UserCharacterStoryUnlock.builder()
                .userId(1L)
                .userCharacterId(1L)
                .storyFragmentId(12L)
                .memoryKey("nova_lv1_easter_001")
                .unlockedLevel(1)
                .build();

        when(userCharacterRepository.findById(1L)).thenReturn(Optional.of(character));
        when(userCharacterStoryUnlockRepository.findByUserIdAndUserCharacterIdOrderByUnlockedAtDesc(
                eq(1L),
                eq(1L),
                any(Pageable.class)
        )).thenReturn(List.of(secondUnlock, firstUnlock));
        when(characterStoryFragmentRepository.findAllById(List.of(12L, 11L)))
                .thenReturn(List.of(first, second));
        when(characterStoryFragmentRepository.countByActiveTrueAndCharacterTypeCodeInAndFragmentTypeIn(
                anyList(),
                anyList()
        )).thenReturn(2L);
        when(userCharacterStoryUnlockRepository.countByUserIdAndUserCharacterId(1L, 1L))
                .thenReturn(2L);
        when(characterStoryFragmentRepository
                .findByActiveTrueAndCharacterTypeCodeInAndFragmentTypeInOrderByMinLevelAscSortOrderAscIdAsc(
                        anyList(),
                        anyList()
                ))
                .thenReturn(List.of(first, second));
        when(userCharacterStoryUnlockRepository.findByUserIdAndUserCharacterIdAndStoryFragmentIdIn(
                eq(1L),
                eq(1L),
                anyCollection()
        )).thenReturn(List.of(firstUnlock, secondUnlock));

        var response = characterService.getCharacterTalkContext(1L, 1L, 5);

        assertEquals(1L, response.characterId());
        assertEquals("NOVA", response.characterTypeCode());
        assertEquals("Nova", response.characterName());
        assertEquals(50, response.hunger().value());
        assertNotNull(response.growth());
        assertEquals(2, response.memories().size());
        assertEquals("nova_lv1_easter_001", response.memories().get(0).memoryKey());
        assertEquals("EASTER_EGG", response.memories().get(0).fragmentType());
        assertEquals(1, response.memories().get(0).unlockedLevel());
        assertEquals("nova_lv1_lore_001", response.memories().get(1).memoryKey());
        assertEquals(2, response.storyProgress().unlockedMemoryCount());
        assertEquals(2, response.storyProgress().totalMemoryCount());
        assertTrue(response.storyProgress().allUnlocked());
        assertNull(response.storyProgress().nextMemoryHint());
        verify(characterStoryFragmentRepository).findAllById(List.of(12L, 11L));
    }

    @Test
    @DisplayName("돌봄 활동 - 멱등키가 null 또는 빈 값이면 예외 발생")
    void testPerformCareAction_IdempotencyKeyNullOrEmpty() {
        // null 멱등키
        assertEquals(CharacterErrorCode.INVALID_IDEMPOTENCY_KEY, assertThrows(CharacterException.class, () ->
                characterService.performCareAction(1L, 1L, "FEED", null, null)
        ).getErrorCode());

        // 빈 멱등키
        assertEquals(CharacterErrorCode.INVALID_IDEMPOTENCY_KEY, assertThrows(CharacterException.class, () ->
                characterService.performCareAction(1L, 1L, "FEED", null, "")
        ).getErrorCode());

        // 공백 멱등키
        assertEquals(CharacterErrorCode.INVALID_IDEMPOTENCY_KEY, assertThrows(CharacterException.class, () ->
                characterService.performCareAction(1L, 1L, "FEED", null, "   ")
        ).getErrorCode());
    }

    @Test
    @DisplayName("돌봄 활동 - 이미 처리된 멱등키면 저장된 돌봄 로그를 재사용")
    void testPerformCareAction_ExistingIdempotencyKey() {
        String key = "test-idempotency-key";
        CharacterCareLog cachedLog = CharacterCareLog.builder()
                .userId(1L)
                .characterId(1L)
                .itemId(null)
                .actionType(ActionType.FEED)
                .beforeStateJson("{\"fullness\":50,\"energy\":50,\"affection\":50}")
                .afterStateJson("{\"fullness\":80,\"energy\":50,\"affection\":50}")
                .idempotencyKey(key)
                .build();

        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(cachedLog));

        CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", null, key);

        assertNotNull(response);
        assertEquals("FEED", response.actionType());
        assertEquals(50, response.beforeStates().hunger());
        assertEquals(80, response.afterStates().hunger());
        assertEquals("먹는 중... 빛도 맛이 있구나.", response.characterMessage());
        assertEquals(0, response.expGained());
        assertFalse(response.levelUp());
        assertNotNull(response.beforeGrowth());
        assertNotNull(response.afterGrowth());

        // 캐시된 돌봄 로그를 재사용하므로 캐릭터 조회/저장은 수행하지 않는다.
        verifyNoInteractions(userCharacterRepository);
        verify(characterCareLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("돌봄 활동 - 새로운 멱등키면 돌봄을 정상 수행")
    void testPerformCareAction_NewIdempotencyKey() {
        String key = "new-idempotency-key";
        Long itemId = 10L;
        
        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));

        CharacterCareLog savedCareLog = CharacterCareLog.builder()
                .userId(1L)
                .characterId(1L)
                .itemId(itemId)
                .actionType(ActionType.FEED)
                .beforeStateJson("{\"fullness\":50,\"energy\":50,\"affection\":50}")
                .afterStateJson("{\"fullness\":80,\"energy\":50,\"affection\":50}")
                .idempotencyKey(key)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(savedCareLog, "id", 123L);
        when(characterCareLogRepository.save(any(CharacterCareLog.class))).thenReturn(savedCareLog);

        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("맛있는 사료")
                .setItemType("CONSUMABLE")
                .setEffectType("FOOD")
                .setQuantity(5)
                .build();
        com.p5laris.proto.item.v1.GetUserItemsResponse ownedItems = com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                .addItems(userItem)
                .build();
        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(ownedItems);

        com.p5laris.proto.item.v1.UseItemResponse useItemResponse = com.p5laris.proto.item.v1.UseItemResponse.newBuilder()
                .setUsageId(1L)
                .setUserItemId(100L)
                .setItemId(itemId)
                .setQuantityUsed(1)
                .setRemainingQuantity(4)
                .build();
        when(itemStub.useItem(any(com.p5laris.proto.item.v1.UseItemRequest.class)))
                .thenReturn(useItemResponse);

        try {
            CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", itemId, key);
            assertNotNull(response);
            assertEquals("FEED", response.actionType());
            assertEquals(50, response.beforeStates().hunger());
            assertEquals(80, response.afterStates().hunger()); // +30 fullness recovery
            assertEquals(5, response.expGained());
            assertFalse(response.levelUp());
            assertEquals(0, response.beforeGrowth().exp());
            assertEquals(5, response.afterGrowth().exp());
        } catch (p5laris.character.domain.exception.CharacterException e) {
            throw e;
        }
        
        // 돌봄 로그 저장과 캐릭터 잠금 조회가 수행되는지 확인한다.
        verify(characterCareLogRepository).save(any(CharacterCareLog.class));
        verify(userCharacterRepository).findByIdForUpdate(1L);
        verify(userCharacterRepository, never()).findById(1L);
        verify(itemStub, atLeastOnce()).withDeadlineAfter(1000L, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(itemStub, userCharacterRepository);
        inOrder.verify(itemStub).getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class));
        inOrder.verify(userCharacterRepository).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("돌봄 활동 - 같은 액션 하루 3회 이후에는 돌봄은 성공하지만 경험치는 지급하지 않음")
    void performCareAction_dailyExpLimitPerAction() {
        String key = "care-exp-limit-key";
        Long itemId = 10L;

        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterCareLogRepository.findByCharacterIdAndActionTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any()
        )).thenReturn(List.of(
                careLogWithExp("limit-1", 5),
                careLogWithExp("limit-2", 5),
                careLogWithExp("limit-3", 5)
        ));
        when(characterCareLogRepository.save(any(CharacterCareLog.class))).thenAnswer(invocation -> {
            CharacterCareLog log = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(log, "id", 123L);
            return log;
        });
        mockOwnedCareItem(itemId, "FOOD");
        when(itemStub.useItem(any(com.p5laris.proto.item.v1.UseItemRequest.class)))
                .thenReturn(com.p5laris.proto.item.v1.UseItemResponse.newBuilder()
                        .setUsageId(1L)
                        .setItemId(itemId)
                        .setQuantityUsed(1)
                        .setRemainingQuantity(1)
                        .build());

        CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", itemId, key);

        assertEquals(0, response.expGained());
        assertFalse(response.levelUp());
        assertEquals(0, response.beforeGrowth().exp());
        assertEquals(0, response.afterGrowth().exp());
        assertEquals(80, response.afterStates().hunger());
    }

    @Test
    @DisplayName("돌봄 활동 - 돌봄 경험치로 레벨 경계에 도달하면 levelUp 반환")
    void performCareAction_levelUpByCareExp() {
        String key = "care-level-up-key";
        Long itemId = 10L;
        org.springframework.test.util.ReflectionTestUtils.setField(character, "exp", 195);
        org.springframework.test.util.ReflectionTestUtils.setField(character, "level", 1);

        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterCareLogRepository.save(any(CharacterCareLog.class))).thenAnswer(invocation -> {
            CharacterCareLog log = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(log, "id", 123L);
            return log;
        });
        mockOwnedCareItem(itemId, "FOOD");
        when(itemStub.useItem(any(com.p5laris.proto.item.v1.UseItemRequest.class)))
                .thenReturn(com.p5laris.proto.item.v1.UseItemResponse.newBuilder()
                        .setUsageId(1L)
                        .setItemId(itemId)
                        .setQuantityUsed(1)
                        .setRemainingQuantity(1)
                        .build());

        CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", itemId, key);

        assertEquals(5, response.expGained());
        assertTrue(response.levelUp());
        assertEquals(1, response.beforeGrowth().level());
        assertEquals(195, response.beforeGrowth().exp());
        assertEquals(2, response.afterGrowth().level());
        assertEquals(200, response.afterGrowth().exp());
    }

    @Test
    @DisplayName("캐릭터 경험치 지급 - 미션 완료 경험치를 지급하고 로그를 저장")
    void grantCharacterExp_success() {
        String key = "MISSION_CHARACTER_EXP:100";
        when(characterExpLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(characterExpLogRepository.findBySourceTypeAndSourceId(CharacterExpSourceType.MISSION_COMPLETION, 100L))
                .thenReturn(Optional.empty());
        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterExpLogRepository.save(any(CharacterExpLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = characterService.grantCharacterExp(
                1L,
                1L,
                "MISSION_COMPLETION",
                100L,
                15,
                key
        );

        assertEquals(15, response.expGained());
        assertFalse(response.alreadyProcessed());
        assertEquals(0, response.beforeGrowth().exp());
        assertEquals(15, response.afterGrowth().exp());
        assertEquals(15, character.getExp());
        verify(userCharacterRepository).findByIdForUpdate(1L);
        verify(characterExpLogRepository).save(any(CharacterExpLog.class));
    }

    @Test
    @DisplayName("캐릭터 경험치 지급 - 같은 멱등키는 저장된 로그를 재사용하고 중복 지급하지 않음")
    void grantCharacterExp_duplicateIdempotency_replaysLog() {
        String key = "MISSION_CHARACTER_EXP:100";
        CharacterExpLog existingLog = CharacterExpLog.builder()
                .userId(1L)
                .characterId(1L)
                .sourceType(CharacterExpSourceType.MISSION_COMPLETION)
                .sourceId(100L)
                .idempotencyKey(key)
                .expAmount(10)
                .beforeExp(0)
                .afterExp(10)
                .beforeLevel(1)
                .afterLevel(1)
                .build();
        when(characterExpLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existingLog));

        var response = characterService.grantCharacterExp(
                1L,
                1L,
                "MISSION_COMPLETION",
                100L,
                10,
                key
        );

        assertEquals(10, response.expGained());
        assertTrue(response.alreadyProcessed());
        assertEquals(0, character.getExp());
        verify(userCharacterRepository, never()).findByIdForUpdate(any());
        verify(characterExpLogRepository, never()).save(any(CharacterExpLog.class));
    }

    @Test
    @DisplayName("돌봄 활동 - 아이템 사용 실패 시 빠르게 실패하고 아이템 에러 코드를 캐릭터 예외로 매핑")
    void performCareAction_itemUseFailed_fastFail() {
        String key = "care-item-fail-key";
        Long itemId = 10L;

        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));

        CharacterCareLog savedCareLog = CharacterCareLog.builder()
                .userId(1L)
                .characterId(1L)
                .itemId(itemId)
                .actionType(ActionType.FEED)
                .beforeStateJson("{\"fullness\":50,\"energy\":50,\"affection\":50}")
                .afterStateJson("{\"fullness\":80,\"energy\":50,\"affection\":50}")
                .idempotencyKey(key)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(savedCareLog, "id", 123L);
        when(characterCareLogRepository.save(any(CharacterCareLog.class))).thenReturn(savedCareLog);

        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("별사탕밥")
                .setItemType("CONSUMABLE")
                .setEffectType("FOOD")
                .setQuantity(1)
                .build();
        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                        .addItems(userItem)
                        .build());
        when(itemStub.useItem(any(com.p5laris.proto.item.v1.UseItemRequest.class)))
                .thenThrow(statusException("ITEM-006"));

        CharacterException exception = assertThrows(
                CharacterException.class,
                () -> characterService.performCareAction(1L, 1L, "FEED", itemId, key)
        );

        assertEquals(CharacterErrorCode.ITEM_QUANTITY_NOT_ENOUGH, exception.getErrorCode());
        verify(itemStub, atLeastOnce()).withDeadlineAfter(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("돌봄 활동 - 아이템 timeout은 돌보기 전체 실패로 처리")
    void performCareAction_itemTimeout_fastFail() {
        String key = "care-item-timeout-key";
        Long itemId = 10L;

        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(character));
        when(characterCareLogRepository.save(any(CharacterCareLog.class))).thenAnswer(invocation -> {
            CharacterCareLog log = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(log, "id", 123L);
            return log;
        });

        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("별사탕밥")
                .setItemType("CONSUMABLE")
                .setEffectType("FOOD")
                .setQuantity(1)
                .build();
        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                        .addItems(userItem)
                        .build());
        when(itemStub.useItem(any(com.p5laris.proto.item.v1.UseItemRequest.class)))
                .thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

        CharacterException exception = assertThrows(
                CharacterException.class,
                () -> characterService.performCareAction(1L, 1L, "FEED", itemId, key)
        );

        assertEquals(CharacterErrorCode.ITEM_SERVICE_CALL_FAILED, exception.getErrorCode());
        verify(itemStub, atLeastOnce()).withDeadlineAfter(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("스킨 장착 - 보유한 스킨이면 정상 장착")
    void equipSkin_ownedSkin_success() {
        // 준비
        Long characterId = 1L;
        Long userId = 1L;
        Long itemId = 10L;

        when(userCharacterRepository.findById(characterId)).thenReturn(Optional.of(character));

        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("푸른 새벽 스킨")
                .build();

        com.p5laris.proto.item.v1.GetUserItemsResponse ownedItems = com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                .addItems(userItem)
                .build();

        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(ownedItems);

        // 실행
        var response = characterService.equipSkin(characterId, userId, itemId);

        // 검증
        assertNotNull(response);
        assertEquals(itemId, response.equippedSkinId());
        assertEquals(itemId, character.getEquippedSkinId());
    }

    @Test
    @DisplayName("스킨 장착 - 보유하지 않은 스킨이면 예외 발생")
    void equipSkin_notOwnedSkin_throwsException() {
        // 준비
        Long characterId = 1L;
        Long userId = 1L;
        Long itemId = 10L;

        when(userCharacterRepository.findById(characterId)).thenReturn(Optional.of(character));

        com.p5laris.proto.item.v1.GetUserItemsResponse emptyItems = com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                .build();

        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(emptyItems);

        // 실행 및 검증
        p5laris.character.domain.exception.CharacterException exception = assertThrows(
                p5laris.character.domain.exception.CharacterException.class,
                () -> characterService.equipSkin(characterId, userId, itemId)
        );
        assertEquals(p5laris.character.domain.exception.CharacterErrorCode.ITEM_NOT_OWNED, exception.getErrorCode());
    }

    private List<CharacterAsset> createMockAssets(CharacterType type) {
        return List.of(
                CharacterAsset.builder().characterType(type).assetType("IDLE").assetUrl("http://cdn/idle.png").build(),
                CharacterAsset.builder().characterType(type).assetType("HUNGRY").assetUrl("http://cdn/hungry.png").build(),
                CharacterAsset.builder().characterType(type).assetType("LOW_ENERGY").assetUrl("http://cdn/lowEnergy.png").build(),
                CharacterAsset.builder().characterType(type).assetType("LONELY").assetUrl("http://cdn/lonely.png").build()
        );
    }

    @Test
    @DisplayName("내 캐릭터 조회 - 기분이 IDLE 일 때 기본 IDLE 이미지 반환")
    void getMyCharacter_idleMood_returnsIdleUrl() {
        // 준비
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // 실행
        var response = characterService.getMyCharacter(1L);

        // 검증
        assertNotNull(response);
        assertEquals("http://cdn/idle.png", response.currentAssetUrl());
        assertEquals("http://cdn/idle.png", response.assetUrls().get("idle"));
        assertEquals("Nova", response.name());
        assertNotNull(response.growth());
        assertEquals(1, response.growth().level());
        assertEquals(0, response.growth().exp());
        assertEquals("BABY", response.growth().growthStage());
    }

    @Test
    @DisplayName("내 캐릭터 조회 - 기분이 HUNGRY 일 때 HUNGRY 이미지 반환")
    void getMyCharacter_hungryMood_returnsHungryUrl() {
        // 준비
        org.springframework.test.util.ReflectionTestUtils.setField(character, "fullness", 30);
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // 실행
        var response = characterService.getMyCharacter(1L);

        // 검증
        assertNotNull(response);
        assertEquals("http://cdn/hungry.png", response.currentAssetUrl());
    }

    @Test
    @DisplayName("내 캐릭터 조회 - 기분이 LOW_ENERGY 일 때 LOW_ENERGY 이미지 반환")
    void getMyCharacter_lowEnergyMood_returnsLowEnergyUrl() {
        // 준비
        org.springframework.test.util.ReflectionTestUtils.setField(character, "energy", 30);
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // 실행
        var response = characterService.getMyCharacter(1L);

        // 검증
        assertNotNull(response);
        assertEquals("http://cdn/lowEnergy.png", response.currentAssetUrl());
    }

    @Test
    @DisplayName("내 캐릭터 조회 - 기분이 LONELY 일 때 LONELY 이미지 반환")
    void getMyCharacter_lonelyMood_returnsLonelyUrl() {
        // 준비
        org.springframework.test.util.ReflectionTestUtils.setField(character, "affection", 30);
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // 실행
        var response = characterService.getMyCharacter(1L);

        // 검증
        assertNotNull(response);
        assertEquals("http://cdn/lonely.png", response.currentAssetUrl());
    }

    @Test
    @DisplayName("내 캐릭터 조회 - 스킨이 장착되어 있고 포만감이 낮으면 스킨 HUNGRY 이미지 반환")
    void getMyCharacter_equippedSkin_returnsSkinHungryUrl() {
        // 준비
        org.springframework.test.util.ReflectionTestUtils.setField(character, "fullness", 30);
        org.springframework.test.util.ReflectionTestUtils.setField(character, "equippedSkinId", 100L);

        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        com.p5laris.proto.item.v1.GetSkinAssetsResponse skinResponse = com.p5laris.proto.item.v1.GetSkinAssetsResponse.newBuilder()
                .putAssetUrls("idle", "http://cdn/skin-idle.png")
                .putAssetUrls("hungry", "http://cdn/skin-hungry.png")
                .build();
        when(itemStub.getSkinAssets(any(com.p5laris.proto.item.v1.GetSkinAssetsRequest.class)))
                .thenReturn(skinResponse);

        // 실행
        var response = characterService.getMyCharacter(1L);

        // 검증
        assertNotNull(response);
        assertEquals("http://cdn/skin-hungry.png", response.currentAssetUrl());
        assertEquals("http://cdn/skin-hungry.png", response.assetUrls().get("hungry"));
        assertEquals("http://cdn/skin-idle.png", response.assetUrls().get("idle"));
    }

    private void mockOwnedCareItem(Long itemId, String effectType) {
        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("돌봄 아이템")
                .setItemType("CONSUMABLE")
                .setEffectType(effectType)
                .setQuantity(5)
                .build();
        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                        .addItems(userItem)
                        .build());
    }

    private CharacterCareLog careLogWithExp(String idempotencyKey, int expGained) {
        return CharacterCareLog.builder()
                .userId(1L)
                .characterId(1L)
                .itemId(10L)
                .actionType(ActionType.FEED)
                .beforeStateJson("{\"fullness\":50,\"energy\":50,\"affection\":50}")
                .afterStateJson("{\"fullness\":80,\"energy\":50,\"affection\":50,\"expGained\":" + expGained + "}")
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private CharacterStoryFragment storyFragment(
            Long id,
            String characterTypeCode,
            int minLevel,
            CharacterStoryFragmentType fragmentType,
            CharacterStoryTriggerType triggerType,
            String memoryKey
    ) {
        CharacterStoryFragment fragment = CharacterStoryFragment.builder()
                .memoryKey(memoryKey)
                .characterTypeCode(characterTypeCode)
                .minLevel(minLevel)
                .fragmentType(fragmentType)
                .triggerType(triggerType)
                .title("기억 조각")
                .message("작은 빛도 남겨둘게.")
                .interpretation("별친구가 오늘의 작은 기록을 지켜주는 것 같아요.")
                .storyText("오늘의 작은 기록은 별친구의 기억 조각 안에 조용히 남았습니다.")
                .sortOrder(1)
                .active(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(fragment, "id", id);
        return fragment;
    }

    private StatusRuntimeException statusException(String description) {
        return Status.INTERNAL.withDescription(description).asRuntimeException();
    }
}
