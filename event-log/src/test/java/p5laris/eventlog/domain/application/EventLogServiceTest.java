package p5laris.eventlog.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.eventlog.domain.domain.dto.EventLogRequest;
import p5laris.eventlog.domain.domain.dto.EventLogResponse;
import p5laris.eventlog.domain.domain.entity.EventLog;
import p5laris.eventlog.domain.domain.repository.EventLogRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventLogServiceTest {

    @Mock
    private EventLogRepository eventLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventLogService eventLogService;

    @Test
    @DisplayName("recordEventLog - 신규 이벤트인 경우 정상 저장 후 recorded=true 리턴")
    void recordEventLog_success() {
        // given
        UUID eventId = UUID.randomUUID();
        EventLogRequest request = new EventLogRequest(
                eventId,
                "USER_SIGNED_UP",
                "user",
                1L,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );

        when(eventLogRepository.existsByEventId(eventId)).thenReturn(false);

        // when
        EventLogResponse response = eventLogService.recordEventLog(request);

        // then
        assertThat(response.recorded()).isTrue();
        verify(eventLogRepository, times(1)).existsByEventId(eventId);
        verify(eventLogRepository, times(1)).save(any(EventLog.class));
    }

    @Test
    @DisplayName("recordEventLog - 중복 이벤트(동일 eventId)인 경우 저장하지 않고 recorded=true 리턴")
    void recordEventLog_duplicate() {
        // given
        UUID eventId = UUID.randomUUID();
        EventLogRequest request = new EventLogRequest(
                eventId,
                "USER_SIGNED_UP",
                "user",
                1L,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );

        when(eventLogRepository.existsByEventId(eventId)).thenReturn(true);

        // when
        EventLogResponse response = eventLogService.recordEventLog(request);

        // then
        assertThat(response.recorded()).isTrue();
        verify(eventLogRepository, times(1)).existsByEventId(eventId);
        verify(eventLogRepository, never()).save(any(EventLog.class));
    }
}
