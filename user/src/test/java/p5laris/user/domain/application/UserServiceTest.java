package p5laris.user.domain.application;

import com.p5laris.proto.user.v1.GetWeatherRegionResponse;
import com.p5laris.proto.user.v1.ListWeatherRegionsResponse;
import com.p5laris.proto.user.v1.UpdateWeatherRegionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.exception.UserException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("날씨 권역 목록을 조회한다")
    void listWeatherRegions() {
        ListWeatherRegionsResponse response = userService.listWeatherRegions();

        assertThat(response.getRegionsCount()).isGreaterThanOrEqualTo(40);
        assertThat(response.getRegionsList())
                .anySatisfy(region -> {
                    assertThat(region.getCode()).isEqualTo("SEOUL");
                    assertThat(region.getDisplayName()).isEqualTo("서울");
                    assertThat(region.getNx()).isEqualTo(60);
                    assertThat(region.getNy()).isEqualTo(127);
                });
    }

    @Test
    @DisplayName("선택한 날씨 권역이 없으면 selected=false를 반환한다")
    void getWeatherRegion_notSelected() {
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        GetWeatherRegionResponse response = userService.getWeatherRegion(userId);

        assertThat(response.getSelected()).isFalse();
        assertThat(response.hasRegion()).isFalse();
    }

    @Test
    @DisplayName("날씨 권역을 저장하고 선택 상태로 반환한다")
    void updateWeatherRegion() {
        Long userId = 1L;
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UpdateWeatherRegionResponse response = userService.updateWeatherRegion(userId, "GYEONGGI_SOUTH");

        assertThat(user.getWeatherRegionCode()).isEqualTo("GYEONGGI_SOUTH");
        assertThat(response.getSelected()).isTrue();
        assertThat(response.getRegion().getCode()).isEqualTo("GYEONGGI_SOUTH");
        assertThat(response.getRegion().getDisplayName()).isEqualTo("경기 남부");
    }

    @Test
    @DisplayName("지원하지 않는 날씨 권역은 거절한다")
    void updateWeatherRegion_invalidCode() {
        assertThatThrownBy(() -> userService.updateWeatherRegion(1L, "UNKNOWN_REGION"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("지원하지 않는 날씨 권역입니다.");
    }

    private User user() {
        return User.builder()
                .email("tester@p5laris.life")
                .nickname("테스터")
                .provider("GOOGLE")
                .role("USER")
                .status("ACTIVE")
                .build();
    }
}
