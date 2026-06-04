package p5laris.mission.domain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserMissionRepository extends JpaRepository<UserMission, Long> {

    // 오늘 유저에게 진행 중인 현재 미션을 찾는다.
    // characterId를 조건에 넣지 않는 이유는 현재 미션 정책이 "캐릭터별"이 아니라 "유저별"이기 때문이다.
    Optional<UserMission> findFirstByUserIdAndMissionDateAndStatusInOrderByStackOrderDesc(
            Long userId,
            LocalDate missionDate,
            Collection<UserMissionStatus> statuses
    );

    // 새 미션 생성 전에 OFFERED/ANSWERING 상태 미션이 이미 있는지 확인한다.
    // 애플리케이션 레벨의 1차 중복 생성 방어다.
    boolean existsByUserIdAndMissionDateAndStatusIn(
            Long userId,
            LocalDate missionDate,
            Collection<UserMissionStatus> statuses
    );

    // 특정 날짜에 특정 상태로 남은 미션 수를 센다.
    // 보상 가능 횟수와 거절 가능 횟수는 서로 다른 정책이라 상태별로 따로 계산한다.
    long countByUserIdAndMissionDateAndStatus(
            Long userId,
            LocalDate missionDate,
            UserMissionStatus status
    );

    // 특정 날짜의 미션 히스토리 화면에 보여줄 stack 전체를 순서대로 조회한다.
    // 하루 최대 보상 횟수와 거절 횟수가 작아 별도 pagination 없이 stackOrder 오름차순으로 반환한다.
    List<UserMission> findByUserIdAndMissionDateOrderByStackOrderAsc(Long userId, LocalDate missionDate);

    // 개인화 context에는 최근 미션 일부만 필요하므로 Pageable로 조회량을 제한한다.
    List<UserMission> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // CHALLENGE 미션은 하루에 하나만 제안하기 위해 날짜 단위로 사용 여부를 확인한다.
    boolean existsByUserIdAndMissionDateAndDifficulty(
            Long userId,
            LocalDate missionDate,
            MissionDifficultyType difficulty
    );

    // 방금 fallback으로 저장한 현재 row를 제외하고 오늘 이미 CHALLENGE가 있었는지 확인한다.
    boolean existsByUserIdAndMissionDateAndDifficultyAndIdNot(
            Long userId,
            LocalDate missionDate,
            MissionDifficultyType difficulty,
            Long excludedMissionId
    );

    // missionId만으로 찾지 않고 userId를 함께 확인해 다른 유저의 미션 처리를 막는다.
    Optional<UserMission> findByIdAndUserId(Long id, Long userId);

    // 완료 질문 시작/답변 제출은 보상 중복과 상태 전이가 걸린 흐름이라 row lock을 잡고 처리한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from UserMission m
            where m.id = :id
              and m.userId = :userId
            """)
    Optional<UserMission> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 오늘 stackOrder의 최대값을 구해 다음 미션 순번을 계산한다.
    @Query("""
            select coalesce(max(m.stackOrder), 0)
            from UserMission m
            where m.userId = :userId
              and m.missionDate = :missionDate
            """)
    int findMaxStackOrder(@Param("userId") Long userId, @Param("missionDate") LocalDate missionDate);

    // 오늘 이미 사용한 템플릿 id 목록을 가져온다.
    // 같은 날 동일 seed 미션이 반복 제안되는 것을 피하기 위해 사용한다.
    @Query("""
            select m.missionTemplateId
            from UserMission m
            where m.userId = :userId
              and m.missionDate = :missionDate
              and m.missionTemplateId is not null
            """)
    List<Long> findMissionTemplateIdsByUserIdAndMissionDate(
            @Param("userId") Long userId,
            @Param("missionDate") LocalDate missionDate
    );
}
