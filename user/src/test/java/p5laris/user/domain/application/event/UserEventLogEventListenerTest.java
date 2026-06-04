package p5laris.user.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventLogEventListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private UserEventLogEventListener userEventLogEventListener;

    @Test
    @DisplayName("handle - 이벤트 수신 시 정상적으로 Outbox 테이블에 saveAndFlush 호출")
    void handle_success() throws Exception {
        // given
        UserEventLogEvent event = UserEventLogEvent.userSignedUp(
                1L, "GOOGLE", "USER", "ACTIVE"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mock\":\"payload\"}");

        // when
        userEventLogEventListener.handle(event);

        // then
        verify(objectMapper, times(1)).writeValueAsString(event);
        verify(outboxEventRepository, times(1)).saveAndFlush(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("handle - 직렬화 예외 발생 시 예외를 잡아내고 상위 트랜잭션으로 전파하지 않음 (예외 억제)")
    void handle_exception_suppressed() throws Exception {
        // given
        UserEventLogEvent event = UserEventLogEvent.userSignedUp(
                1L, "GOOGLE", "USER", "ACTIVE"
        );

        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON serialization failed"));

        // when & then
        assertThatNoException().isThrownBy(() -> userEventLogEventListener.handle(event));

        verify(outboxEventRepository, never()).saveAndFlush(any());
    }
}
