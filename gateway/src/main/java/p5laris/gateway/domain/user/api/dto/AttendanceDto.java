package p5laris.gateway.domain.user.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

public class AttendanceDto {

    @Getter
    @Builder
    public static class Response {
        private Long id;
        private String attendanceDate;
        private int rewardStarPiece;
        private int streakCount;
    }

    @Getter
    @Builder
    public static class ListResponse {
        private List<Response> records;
    }
}
