package p5laris.ai.domain.infrastructure.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.character.v1.CharacterGrowth;
import com.p5laris.proto.character.v1.CharacterServiceGrpc;
import com.p5laris.proto.character.v1.CharacterStateDetail;
import com.p5laris.proto.character.v1.CharacterStoryMemory;
import com.p5laris.proto.character.v1.GetCharacterTalkContextRequest;
import com.p5laris.proto.character.v1.GetCharacterTalkContextResponse;
import com.p5laris.proto.mission.v1.GetMissionTalkContextRequest;
import com.p5laris.proto.mission.v1.GetMissionTalkContextResponse;
import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 별친구 대화용 Spring AI Tool 객체를 요청 단위로 만든다.
 *
 * Tool 메서드는 LLM에게 userId/characterId를 입력받지 않고, 서버가 검증해 넘긴 command 값만 사용한다.
 * 덕분에 Tool Calling이 켜져도 사용자가 다른 유저/캐릭터 id를 프롬프트로 주입해 조회하는 경로를 만들지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CharacterTalkToolsFactory {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    @GrpcClient("mission")
    private MissionServiceGrpc.MissionServiceBlockingStub missionStub;

    private final AiCharacterTalkProperties properties;
    private final ObjectMapper objectMapper;

    public Object create(CharacterTalkGenerationCommand command) {
        return new CharacterTalkTools(
                command,
                characterStub,
                missionStub,
                properties,
                objectMapper
        );
    }

    /**
     * Spring AI가 reflection으로 읽는 요청 단위 Tool 묶음이다.
     *
     * 내부 gRPC 결과는 한 번만 조회해 캐시하고, 실패 시에는 대화 전체를 실패시키지 않도록
     * UNAVAILABLE JSON을 반환한다.
     */
    @Slf4j
    public static final class CharacterTalkTools {

        private static final String STATUS_AVAILABLE = "AVAILABLE";
        private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

        private final CharacterTalkGenerationCommand command;
        private final CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;
        private final MissionServiceGrpc.MissionServiceBlockingStub missionStub;
        private final AiCharacterTalkProperties properties;
        private final ObjectMapper objectMapper;
        private GetCharacterTalkContextResponse cachedCharacterContext;
        private boolean characterContextLoaded;
        private GetMissionTalkContextResponse cachedMissionContext;
        private boolean missionContextLoaded;

        private CharacterTalkTools(
                CharacterTalkGenerationCommand command,
                CharacterServiceGrpc.CharacterServiceBlockingStub characterStub,
                MissionServiceGrpc.MissionServiceBlockingStub missionStub,
                AiCharacterTalkProperties properties,
                ObjectMapper objectMapper
        ) {
            this.command = command;
            this.characterStub = characterStub;
            this.missionStub = missionStub;
            this.properties = properties;
            this.objectMapper = objectMapper;
        }

        @Tool(description = "현재 별친구의 포만감, 에너지, 애정, 성장 레벨과 성장 단계를 조회한다. 별친구의 상태, 컨디션, 성장, 레벨을 말할 때 사용한다.")
        public String getCharacterStatus() {
            Optional<GetCharacterTalkContextResponse> context = loadCharacterContext("getCharacterStatus");
            if (context.isEmpty()) {
                return unavailable("getCharacterStatus", "CHARACTER_CONTEXT_UNAVAILABLE");
            }

            GetCharacterTalkContextResponse response = context.get();
            return available("getCharacterStatus", Map.of(
                    "characterId", response.getCharacterId(),
                    "characterTypeCode", response.getCharacterTypeCode(),
                    "characterName", response.getCharacterName(),
                    "states", Map.of(
                            "hunger", state(response.getHunger()),
                            "energy", state(response.getEnergy()),
                            "affection", state(response.getAffection())
                    ),
                    "growth", growth(response.getGrowth())
            ));
        }

        @Tool(description = "현재 별친구에게 해금된 기억 조각을 조회한다. 별친구의 과거, 서사, 기억, 어디서 왔는지와 관련된 질문에 사용한다.")
        public String getUnlockedCharacterMemories() {
            Optional<GetCharacterTalkContextResponse> context = loadCharacterContext("getUnlockedCharacterMemories");
            if (context.isEmpty()) {
                return unavailable("getUnlockedCharacterMemories", "CHARACTER_MEMORY_UNAVAILABLE");
            }

            List<Map<String, Object>> memories = context.get().getMemoriesList().stream()
                    .map(this::memory)
                    .toList();
            return available("getUnlockedCharacterMemories", Map.of(
                    "memoryCount", memories.size(),
                    "memories", memories
            ));
        }

        @Tool(description = "오늘 제안된 미션, 완료/거절 상태, 현재 진행 중인 미션을 조회한다. 오늘 미션이나 지금 할 일을 물어볼 때 사용한다.")
        public String getTodayMissions() {
            Optional<GetMissionTalkContextResponse> context = loadMissionContext("getTodayMissions");
            if (context.isEmpty()) {
                return unavailable("getTodayMissions", "TODAY_MISSION_CONTEXT_UNAVAILABLE");
            }

            return available("getTodayMissions", Map.of(
                    "todayMissionContext", readJson(context.get().getTodayMissionContextJson())
            ));
        }

        @Tool(description = "최근 미션, 최근 답변 미리보기, 만족도/거절 피드백, 루틴 흐름을 조회한다. 사용자의 최근 루틴 흐름이나 반복 패턴을 참고할 때 사용한다.")
        public String getRecentMissionRoutineSummary() {
            Optional<GetMissionTalkContextResponse> context = loadMissionContext("getRecentMissionRoutineSummary");
            if (context.isEmpty()) {
                return unavailable("getRecentMissionRoutineSummary", "RECENT_ROUTINE_CONTEXT_UNAVAILABLE");
            }

            return available("getRecentMissionRoutineSummary", Map.of(
                    "recentRoutineContext", readJson(context.get().getRecentRoutineContextJson())
            ));
        }

        @Tool(description = "현재 시간대, 사용자 날씨 권역, 날씨 snapshot과 날씨 기반 미션 정책을 조회한다. 날씨, 밤/새벽/아침, 밖에 나가기 같은 환경 질문에 사용한다.")
        public String getWeatherAndTimeContext() {
            Optional<GetMissionTalkContextResponse> context = loadMissionContext("getWeatherAndTimeContext");
            if (context.isEmpty()) {
                return unavailable("getWeatherAndTimeContext", "ENVIRONMENT_CONTEXT_UNAVAILABLE");
            }

            return available("getWeatherAndTimeContext", Map.of(
                    "environmentContext", readJson(context.get().getEnvironmentContextJson())
            ));
        }

        private Optional<GetCharacterTalkContextResponse> loadCharacterContext(String toolName) {
            if (characterContextLoaded) {
                return Optional.ofNullable(cachedCharacterContext);
            }

            characterContextLoaded = true;
            try {
                cachedCharacterContext = characterStub()
                        .getCharacterTalkContext(GetCharacterTalkContextRequest.newBuilder()
                                .setUserId(command.userId())
                                .setCharacterId(command.characterId())
                                .setMemoryLimit(5)
                                .build());
                return Optional.of(cachedCharacterContext);
            } catch (StatusRuntimeException e) {
                log.warn("별친구 대화 Tool gRPC 호출 실패. requestId={}, toolName={}, statusCode={}",
                        command.requestId(), toolName, e.getStatus().getCode());
                return Optional.empty();
            } catch (RuntimeException e) {
                log.warn("별친구 대화 Tool 처리 중 알 수 없는 예외가 발생했습니다. requestId={}, toolName={}, exception={}",
                        command.requestId(), toolName, e.getClass().getSimpleName());
                return Optional.empty();
            }
        }

        private Optional<GetMissionTalkContextResponse> loadMissionContext(String toolName) {
            if (missionContextLoaded) {
                return Optional.ofNullable(cachedMissionContext);
            }

            missionContextLoaded = true;
            try {
                cachedMissionContext = missionStub()
                        .getMissionTalkContext(GetMissionTalkContextRequest.newBuilder()
                                .setUserId(command.userId())
                                .build());
                return Optional.of(cachedMissionContext);
            } catch (StatusRuntimeException e) {
                log.warn("별친구 대화 Tool gRPC 호출 실패. requestId={}, toolName={}, statusCode={}",
                        command.requestId(), toolName, e.getStatus().getCode());
                return Optional.empty();
            } catch (RuntimeException e) {
                log.warn("별친구 대화 Tool 처리 중 알 수 없는 예외가 발생했습니다. requestId={}, toolName={}, exception={}",
                        command.requestId(), toolName, e.getClass().getSimpleName());
                return Optional.empty();
            }
        }

        private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub() {
            return characterStub.withDeadlineAfter(properties.normalizedToolDeadlineMs(), TimeUnit.MILLISECONDS);
        }

        private MissionServiceGrpc.MissionServiceBlockingStub missionStub() {
            return missionStub.withDeadlineAfter(properties.normalizedToolDeadlineMs(), TimeUnit.MILLISECONDS);
        }

        private String available(String toolName, Map<String, Object> payload) {
            logTool(toolName, STATUS_AVAILABLE);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("toolName", toolName);
            result.put("status", STATUS_AVAILABLE);
            result.putAll(payload);
            return toJson(result);
        }

        private String unavailable(String toolName, String reason) {
            logTool(toolName, STATUS_UNAVAILABLE);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("toolName", toolName);
            result.put("status", STATUS_UNAVAILABLE);
            result.put("reason", reason);
            return toJson(result);
        }

        private Map<String, Object> state(CharacterStateDetail detail) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("value", detail.getValue());
            state.put("label", detail.getLabel());
            state.put("grade", detail.getGrade());
            return state;
        }

        private Map<String, Object> growth(CharacterGrowth growth) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("level", growth.getLevel());
            value.put("exp", growth.getExp());
            value.put("currentLevelExp", growth.getCurrentLevelExp());
            value.put("nextLevelExp", growth.getNextLevelExp());
            value.put("expToNextLevel", growth.getExpToNextLevel());
            value.put("progressPercent", growth.getProgressPercent());
            value.put("growthStage", growth.getGrowthStage());
            value.put("growthStageLabel", growth.getGrowthStageLabel());
            value.put("maxLevel", growth.getMaxLevel());
            return value;
        }

        private Map<String, Object> memory(CharacterStoryMemory memory) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("memoryKey", memory.getMemoryKey());
            value.put("title", memory.getTitle());
            value.put("storyText", memory.getStoryText());
            return value;
        }

        private Object readJson(String json) {
            if (json == null || json.isBlank()) {
                return Map.of();
            }

            try {
                JsonNode node = objectMapper.readTree(json);
                return node == null || node.isNull() ? Map.of() : node;
            } catch (Exception e) {
                return Map.of();
            }
        }

        private String toJson(Map<String, Object> value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return "{\"status\":\"UNAVAILABLE\",\"reason\":\"TOOL_RESULT_SERIALIZATION_FAILED\"}";
            }
        }

        private void logTool(String toolName, String status) {
            log.info("별친구 대화 Tool 호출 결과. requestId={}, toolName={}, status={}",
                    command.requestId(), toolName, status);
        }
    }
}
