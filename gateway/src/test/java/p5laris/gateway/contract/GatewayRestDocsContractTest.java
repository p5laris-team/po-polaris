package p5laris.gateway.contract;

import com.p5laris.proto.item.v1.PurchaseItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import p5laris.gateway.domain.character.api.CharacterController;
import p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse;
import p5laris.gateway.domain.character.api.dto.MyCharacterResponse;
import p5laris.gateway.domain.character.infrastructure.grpc.CharacterGatewayService;
import p5laris.gateway.domain.item.api.ItemController;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;
import p5laris.gateway.domain.mission.api.MissionController;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;
import p5laris.gateway.domain.user.api.PaymentController;
import p5laris.gateway.domain.user.api.UserController;
import p5laris.gateway.domain.user.api.dto.PaymentDto;
import p5laris.gateway.domain.user.api.dto.UserDto;
import p5laris.gateway.domain.user.infrastructure.grpc.PaymentGatewayService;
import p5laris.gateway.domain.user.infrastructure.grpc.UserGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.exception.GlobalExceptionHandler;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class GatewayRestDocsContractTest {

    private static final Long USER_ID = 1L;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private MissionGatewayService missionGatewayService;

    @Mock
    private CharacterGatewayService characterGatewayService;

    @Mock
    private UserGatewayService userGatewayService;

    @Mock
    private ItemGatewayService itemGatewayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(
                        new PaymentController(paymentGatewayService),
                        new MissionController(missionGatewayService),
                        new CharacterController(characterGatewayService),
                        new UserController(userGatewayService),
                        new ItemController(itemGatewayService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new TestLoginUserIdArgumentResolver())
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    @DisplayName("결제 주문 생성 API 계약")
    void createPaymentOrder() throws Exception {
        when(paymentGatewayService.createOrder(USER_ID, 1_000, 100))
                .thenReturn(new PaymentDto.OrderResponse("order-20260612", 1_000, 100, "READY"));

        mockMvc.perform(post("/api/payment/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 1000,
                                  "starPieces": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andDo(document("payment-order-create",
                        relaxedRequestFields(
                                fieldWithPath("amount").description("결제 금액"),
                                fieldWithPath("starPieces").description("충전할 별조각 수")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("success").description("요청 성공 여부"),
                                fieldWithPath("data.orderNo").description("결제 주문 번호"),
                                fieldWithPath("data.status").description("결제 주문 상태")
                        )));
    }

    @Test
    @DisplayName("미션 완료 답변 API 계약")
    void submitMissionCompletionAnswer() throws Exception {
        MissionDto.CompletionAnswerResponse response = new MissionDto.CompletionAnswerResponse(
                10L,
                "COMPLETED",
                new MissionDto.CompletionAnswer("물을 마셨어요.", "2026-06-12T12:00:00"),
                new MissionDto.MissionReward(10, 1),
                new MissionDto.WalletSnapshot(110),
                "PAID",
                null,
                "좋아, 오늘도 해냈네."
        );
        when(missionGatewayService.submitCompletionAnswer(
                eq(USER_ID),
                eq(10L),
                any(MissionDto.SubmitCompletionAnswerRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/mission/v1/missions/{missionId}/completion-answers", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": "물을 마셨어요."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.rewardStatus").value("PAID"))
                .andDo(document("mission-completion-answer",
                        pathParameters(parameterWithName("missionId").description("완료할 미션 ID")),
                        relaxedRequestFields(
                                fieldWithPath("answer").description("미션 완료 답변, 최대 300자")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("success").description("요청 성공 여부"),
                                fieldWithPath("data.status").description("미션 상태"),
                                fieldWithPath("data.rewardStatus").description("보상 지급 상태"),
                                fieldWithPath("data.wallet.starPiece").description("지급 후 별조각 잔액")
                        )));
    }

    @Test
    @DisplayName("내 캐릭터 조회 API 계약")
    void getMyCharacter() throws Exception {
        when(characterGatewayService.getMyCharacter(USER_ID)).thenReturn(
                MyCharacterResponse.builder()
                        .id(20L)
                        .name("노바")
                        .characterTypeCode("NOVA")
                        .currentAssetUrl("https://cdn.example.com/nova.png")
                        .assetUrls(Map.of("DEFAULT", "https://cdn.example.com/nova.png"))
                        .active(true)
                        .states(MyCharacterResponse.States.builder()
                                .hunger(70)
                                .energy(80)
                                .affection(60)
                                .build())
                        .growth(CharacterGrowthResponse.builder()
                                .level(2)
                                .exp(250)
                                .currentLevelExp(200)
                                .nextLevelExp(600)
                                .expToNextLevel(350)
                                .progressPercent(12)
                                .growthStage("GROWING")
                                .growthStageLabel("성장 중")
                                .maxLevel(false)
                                .build())
                        .build()
        );

        mockMvc.perform(get("/api/character/v1/characters/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characterTypeCode").value("NOVA"))
                .andDo(document("character-me-get",
                        relaxedResponseFields(
                                fieldWithPath("success").description("요청 성공 여부"),
                                fieldWithPath("data.id").description("캐릭터 ID"),
                                fieldWithPath("data.characterTypeCode").description("캐릭터 타입 코드"),
                                fieldWithPath("data.states").description("현재 상태 수치"),
                                fieldWithPath("data.growth").description("성장 정보")
                        )));
    }

    @Test
    @DisplayName("사용자 프로필 조회 API 계약")
    void getUserProfile() throws Exception {
        when(userGatewayService.getUser(USER_ID)).thenReturn(UserDto.builder()
                .id(USER_ID)
                .email("user@example.com")
                .nickname("폴라리스")
                .provider("GOOGLE")
                .role("USER")
                .status("ACTIVE")
                .build());

        mockMvc.perform(get("/api/user/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("폴라리스"))
                .andDo(document("user-me-get",
                        relaxedResponseFields(
                                fieldWithPath("success").description("요청 성공 여부"),
                                fieldWithPath("data.id").description("사용자 ID"),
                                fieldWithPath("data.email").description("사용자 이메일"),
                                fieldWithPath("data.nickname").description("사용자 닉네임"),
                                fieldWithPath("data.provider").description("로그인 제공자"),
                                fieldWithPath("data.status").description("사용자 상태")
                        )));
    }

    @Test
    @DisplayName("아이템 구매 API 계약")
    void purchaseItem() throws Exception {
        when(itemGatewayService.purchaseItem(USER_ID, 30L, 1, "ITEM_PURCHASE:request-1"))
                .thenReturn(PurchaseItemResponse.newBuilder()
                        .setPurchaseId(40L)
                        .setItemId(30L)
                        .setName("별빛 간식")
                        .setQuantity(1)
                        .setPrice(20)
                        .setStarPiece(80)
                        .setTransactionId(50L)
                        .build());

        mockMvc.perform(post("/api/item/v1/item-purchases")
                        .header("Idempotency-Key", "ITEM_PURCHASE:request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itemId": 30,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.starPiece").value(80))
                .andDo(document("item-purchase-create",
                        requestHeaders(
                                headerWithName("Idempotency-Key").description("중복 구매 방지 키")
                        ),
                        relaxedRequestFields(
                                fieldWithPath("itemId").description("구매할 아이템 ID"),
                                fieldWithPath("quantity").description("구매 수량")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("success").description("요청 성공 여부"),
                                fieldWithPath("data.purchaseId").description("구매 요청 ID"),
                                fieldWithPath("data.transactionId").description("별조각 거래 ID"),
                                fieldWithPath("data.wallet.starPiece").description("구매 후 별조각 잔액")
                        )));
    }

    private static class TestLoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(LoginUserId.class)
                    && parameter.getParameterType().equals(Long.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            webRequest.setAttribute("userId", USER_ID, RequestAttributes.SCOPE_REQUEST);
            return USER_ID;
        }
    }
}
