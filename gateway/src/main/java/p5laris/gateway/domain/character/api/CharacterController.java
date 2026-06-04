package p5laris.gateway.domain.character.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.character.api.dto.CharacterTypesResponse;
import p5laris.gateway.domain.character.infrastructure.grpc.CharacterGatewayService;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterGatewayService characterGatewayService;

    /**
     * Get character types (API spec 4.1).
     * GET /api/character/v1/character-types
     */
    @GetMapping("/v1/character-types")
    public ApiResponse<CharacterTypesResponse> getCharacterTypes() {
        return ApiResponse.success(characterGatewayService.getCharacterTypes());
    }

    /**
     * Get character assets (API spec 4.2).
     * GET /api/character/v1/character-types/{characterTypeId}/assets
     */
    @GetMapping("/v1/character-types/{characterTypeId}/assets")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse> getCharacterAssets(
            @org.springframework.web.bind.annotation.PathVariable Long characterTypeId) {
        return ApiResponse.success(characterGatewayService.getCharacterAssets(characterTypeId));
    }

    /**
     * Create a user character (API spec 4.3).
     * POST /api/character/v1/characters
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/characters")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.UserCharacterResponse> createCharacter(
            @p5laris.gateway.global.auth.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.CreateCharacterRequest request) {
        return ApiResponse.success(characterGatewayService.createCharacter(userId, request));
    }

    /**
     * Get my character (API spec 4.4).
     * GET /api/character/v1/characters/me
     */
    @GetMapping("/v1/characters/me")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.MyCharacterResponse> getMyCharacter(
            @p5laris.gateway.global.auth.LoginUserId Long userId) {
        return ApiResponse.success(characterGatewayService.getMyCharacter(userId));
    }

    /**
     * Update character name (API spec 4.5).
     * PATCH /api/character/v1/characters/{characterId}
     */
    @org.springframework.web.bind.annotation.PatchMapping("/v1/characters/{characterId}")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.UpdateCharacterNameResponse> updateCharacterName(
            @org.springframework.web.bind.annotation.PathVariable Long characterId,
            @p5laris.gateway.global.auth.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.UpdateCharacterNameRequest request) {
        return ApiResponse.success(characterGatewayService.updateCharacterName(characterId, userId, request));
    }

    /**
     * Get character status (API spec 4.6).
     * GET /api/character/v1/characters/{characterId}/status
     */
    @GetMapping("/v1/characters/{characterId}/status")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.CharacterStatusResponse> getCharacterStatus(
            @org.springframework.web.bind.annotation.PathVariable Long characterId,
            @p5laris.gateway.global.auth.LoginUserId Long userId) {
        return ApiResponse.success(characterGatewayService.getCharacterStatus(characterId, userId));
    }

    /**
     * Perform care action (API spec 4.7).
     * POST /api/character/v1/characters/{characterId}/care-logs
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/characters/{characterId}/care-logs")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.CareActionResponse> performCareAction(
            @org.springframework.web.bind.annotation.PathVariable Long characterId,
            @p5laris.gateway.global.auth.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String idempotencyKey,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.CareActionRequest request) {
        return ApiResponse.success(characterGatewayService.performCareAction(characterId, userId, idempotencyKey, request));
    }

    /**
     * 캐릭터 터치/상태 트리거에 맞는 대사와 기억 조각을 조회한다.
     * POST /api/character/v1/characters/{characterId}/interactions
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/characters/{characterId}/interactions")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.CharacterInteractionResponse> interactWithCharacter(
            @org.springframework.web.bind.annotation.PathVariable Long characterId,
            @p5laris.gateway.global.auth.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.CharacterInteractionRequest request) {
        return ApiResponse.success(characterGatewayService.interactWithCharacter(characterId, userId, request));
    }

    /**
     * 별친구에게 말을 걸고 SSE로 답변을 조금씩 내려준다.
     * POST /api/character/v1/characters/{characterId}/talk/stream
     */
    @org.springframework.web.bind.annotation.PostMapping(
            value = "/v1/characters/{characterId}/talk/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamCharacterTalk(
            @org.springframework.web.bind.annotation.PathVariable Long characterId,
            @p5laris.gateway.global.auth.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.CharacterTalkStreamRequest request) {
        return characterGatewayService.streamCharacterTalk(characterId, userId, request);
    }

    /**
     * Equip skin on character (API spec 4.8).
     * PUT /api/character/v1/characters/{characterId}/equipped-skin
     */
    @org.springframework.web.bind.annotation.PutMapping("/v1/characters/{characterId}/equipped-skin")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.EquipSkinResponse> equipSkin(
            @org.springframework.web.bind.annotation.PathVariable Long characterId,
            @p5laris.gateway.global.auth.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.EquipSkinRequest request) {
        return ApiResponse.success(characterGatewayService.equipSkin(characterId, userId, request));
    }
}

