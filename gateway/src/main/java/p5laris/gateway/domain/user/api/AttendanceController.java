package p5laris.gateway.domain.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import p5laris.gateway.domain.user.api.dto.AttendanceDto;
import p5laris.gateway.domain.user.infrastructure.grpc.AttendanceGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceGatewayService attendanceGatewayService;

    /**
     * 오늘 출석 기록 생성 및 보상 지급
     * @param userId
     * @return
     */
    @PostMapping("/v1/attendance-records")
    public ApiResponse<AttendanceDto.Response> attend(@LoginUserId Long userId) {
        return ApiResponse.success(attendanceGatewayService.attend(userId));
    }

    /**
     * 출석 기록 조회
     * @param userId
     * @param year
     * @param month
     * @return
     */
    @GetMapping("/v1/attendance-records")
    public ApiResponse<AttendanceDto.ListResponse> getAttendanceRecords(
            @LoginUserId Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return ApiResponse.success(attendanceGatewayService.getAttendanceRecords(userId, year, month));
    }
}
