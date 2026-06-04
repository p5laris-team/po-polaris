package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.AttendanceDto;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceGatewayService {

    @GrpcClient("user")
    private AttendanceServiceGrpc.AttendanceServiceBlockingStub attendanceServiceStub;

    // 오늘 출석 기록 생성 및 보상 지급
    public AttendanceDto.Response attend(Long userId) {
        AttendResponse response = attendanceServiceStub.attend(
                AttendRequest.newBuilder().setUserId(userId).build()
        );

        return toDto(response.getRecord());
    }

    // 출석 기록 조회
    public AttendanceDto.ListResponse getAttendanceRecords(Long userId, int year, int month) {
        GetAttendanceRecordsResponse response = attendanceServiceStub.getAttendanceRecords(
                GetAttendanceRecordsRequest.newBuilder()
                        .setUserId(userId)
                        .setYear(year)
                        .setMonth(month)
                        .build()
        );

        List<AttendanceDto.Response> list = response.getRecordsList().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return AttendanceDto.ListResponse.builder().records(list).build();
    }

    private AttendanceDto.Response toDto(AttendanceRecordDto record) {
        return AttendanceDto.Response.builder()
                .id(record.getId())
                .attendanceDate(record.getAttendanceDate())
                .rewardStarPiece(record.getRewardStarPiece())
                .streakCount(record.getStreakCount())
                .build();
    }
}
