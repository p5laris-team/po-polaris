package p5laris.notification.domain.application;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FcmSenderServiceTest {

    @Test
    void shouldDeactivateToken_returns_true_for_dead_token_errors() {
        assertThat(FcmSenderService.shouldDeactivateToken(MessagingErrorCode.UNREGISTERED)).isTrue();
        assertThat(FcmSenderService.shouldDeactivateToken(MessagingErrorCode.INVALID_ARGUMENT)).isTrue();
    }

    @Test
    void shouldDeactivateToken_returns_false_for_transient_errors() {
        assertThat(FcmSenderService.shouldDeactivateToken(MessagingErrorCode.UNAVAILABLE)).isFalse();
        assertThat(FcmSenderService.shouldDeactivateToken((MessagingErrorCode) null)).isFalse();
    }
}
