package p5laris.gateway.domain.mission.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.exception.GlobalExceptionHandler;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MissionControllerValidationTest {

    private static final Long LOGIN_USER_ID = 1L;

    @Mock
    private MissionGatewayService missionGatewayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MissionController(missionGatewayService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new TestLoginUserIdArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    void 다음_미션_요청의_characterId가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/mission/v1/missions/today-focus/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastMissionId": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void 다음_미션_요청의_lastMissionId가_음수이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/mission/v1/missions/today-focus/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": 10,
                                  "lastMissionId": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void 완료_답변이_공백이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/mission/v1/missions/10/completion-answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void 완료_답변이_300자를_초과하면_400을_반환한다() throws Exception {
        String answer = "a".repeat(301);

        mockMvc.perform(post("/api/mission/v1/missions/10/completion-answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": "%s"
                                }
                                """.formatted(answer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void missionId가_양수가_아니면_400을_반환한다() throws Exception {
        when(missionGatewayService.rejectMission(LOGIN_USER_ID, 0L, null))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(post("/api/mission/v1/missions/0/rejections")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    void 요청_body가_JSON_형식이_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/mission/v1/missions/today-focus/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void 오늘_미션_stack_조회는_현재_미션_id와_목록을_반환한다() throws Exception {
        MissionDto.TodayMissionsResponse response = new MissionDto.TodayMissionsResponse(
                "2026-05-21",
                20,
                2,
                1,
                0,
                19,
                20,
                1,
                19,
                10,
                10,
                12L,
                List.of(new MissionDto.TodayMissionItem(
                        11L,
                        1,
                        "물 한 컵 마시기",
                        "HEALTH",
                        "EASY",
                        10,
                        "COMPLETED",
                        "좋아, 오늘도 한 걸음이야.",
                        "2026-05-21T09:00:00",
                        "2026-05-21T09:10:00",
                        null,
                        "해보고 나서 어땠어?",
                        "물을 마셨어",
                        true
                ))
        );
        when(missionGatewayService.getTodayMissions(LOGIN_USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/mission/v1/missions/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentMissionId").value(12))
                .andExpect(jsonPath("$.data.missions[0].id").value(11))
                .andExpect(jsonPath("$.data.missions[0].completedAt").value("2026-05-21T09:10:00"));
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
            webRequest.setAttribute("userId", LOGIN_USER_ID, RequestAttributes.SCOPE_REQUEST);
            return LOGIN_USER_ID;
        }
    }
}
