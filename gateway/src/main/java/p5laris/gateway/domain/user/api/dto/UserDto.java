package p5laris.gateway.domain.user.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDto {
    private Long id;
    private String email;
    private String nickname;
    private String provider;
    private String role;
    private String status;
}
