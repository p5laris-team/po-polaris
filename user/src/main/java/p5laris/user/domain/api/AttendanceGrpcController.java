package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.AttendanceService;
import p5laris.user.domain.domain.entity.AttendanceRecord;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AttendanceGrpcController extends AttendanceServiceGrpc.AttendanceServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "출석 서비스 처리 중 오류가 발생했습니다.";

    private final AttendanceService attendanceService;

    @Override
    public void attend(AttendRequest request, StreamObserver<AttendResponse> responseObserver) {
        try {
            AttendanceRecord record = attendanceService.attend(request.getUserId());
            
            AttendResponse response = AttendResponse.newBuilder()
                    .setRecord(toDto(record))
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("출석 처리", e));
        }
    }

    @Override
    public void getAttendanceRecords(GetAttendanceRecordsRequest request, StreamObserver<GetAttendanceRecordsResponse> responseObserver) {
        try {
            List<AttendanceRecord> records = attendanceService.getAttendanceRecords(
                    request.getUserId(), request.getYear(), request.getMonth());
            
            List<AttendanceRecordDto> dtos = records.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
                    
            GetAttendanceRecordsResponse response = GetAttendanceRecordsResponse.newBuilder()
                    .addAllRecords(dtos)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("출석 기록 조회", e));
        }
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("출석 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return io.grpc.Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }

    private AttendanceRecordDto toDto(AttendanceRecord record) {
        return AttendanceRecordDto.newBuilder()
                .setId(record.getId())
                .setAttendanceDate(record.getAttendanceDate().toString())
                .setRewardStarPiece(record.getRewardStarPiece())
                .setStreakCount(record.getStreakCount())
                .build();
    }
}
