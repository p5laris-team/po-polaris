package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.domain.entity.AttendanceRecord;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.domain.repository.AttendanceRecordRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    // 출석 보상
    private static final int ATTENDANCE_REWARD = 10;

    @Transactional
    public AttendanceRecord attend(Long userId) {
        LocalDate today = LocalDate.now();

        // 오늘 날짜의 출석 기록 체크
        if (attendanceRecordRepository.existsByUserIdAndAttendanceDate(userId, today)) {
            throw new UserException(UserErrorCode.ALREADY_ATTENDED);
        }

        // 어제 날짜로 출석 기록 조회
        LocalDate yesterday = today.minusDays(1);
        int streakCount = 1;
        
        List<AttendanceRecord> pastRecords = attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                userId, yesterday, yesterday);

        // 출석 기록 있다면 count + 1 (연속 출석)
        if (!pastRecords.isEmpty()) {
            streakCount = pastRecords.get(0).getStreakCount() + 1;
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .userId(userId)
                .attendanceDate(today)
                .rewardStarPiece(ATTENDANCE_REWARD)
                .streakCount(streakCount)
                .build();
                
        attendanceRecordRepository.saveAndFlush(record);

        // 멱등성 키 발급 (중복 지급 방어)
        String dateStr = today.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String idempotencyKey = "ATTENDANCE:" + userId + ":" + dateStr;
        
        StarPieceTransaction transaction = walletService.earnStarPiece(
                userId, 
                ATTENDANCE_REWARD, 
                "ATTENDANCE", 
                "ATTENDANCE", 
                record.getId(), 
                idempotencyKey
        );

        eventPublisher.publishEvent(UserEventLogEvent.attendanceChecked(record));
        eventPublisher.publishEvent(UserEventLogEvent.starPieceEarned(record, transaction));

        return record;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> getAttendanceRecords(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                userId, startDate, endDate);
    }
}
