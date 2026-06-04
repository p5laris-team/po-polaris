package p5laris.mission.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 미션 완료 질문과 사용자의 텍스트 답변을 저장하는 엔티티다.
 *
 * mission_completion_answers 테이블에는 updated_at 컬럼이 없으므로 BaseEntity를 상속하지 않는다.
 * created_at은 DB 기본값으로 채워지고, 애플리케이션에서는 조회만 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mission_completion_answers")
public class MissionCompletionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // 완료 질문 세션이 시작될 때 아직 답변이 비어 있는 질문 row를 만든다.
    public static MissionCompletionAnswer start(Long missionId, Long userId, String questionText) {
        MissionCompletionAnswer answer = new MissionCompletionAnswer();
        answer.missionId = missionId;
        answer.userId = userId;
        answer.questionText = questionText;
        return answer;
    }

    // AI 문구 생성이 성공하면 미리 만들어 둔 fallback 질문을 캐릭터 말투 질문으로 교체한다.
    public void replaceQuestion(String questionText) {
        this.questionText = questionText;
    }

    // 사용자가 답변을 제출하면 답변 본문과 답변 시각을 기록한다.
    public void submit(String answerText, LocalDateTime answeredAt) {
        this.answerText = answerText;
        this.answeredAt = answeredAt;
    }

    // answer_text가 있으면 이미 제출된 답변으로 본다.
    public boolean isAnswered() {
        return answerText != null && !answerText.isBlank();
    }

    // 완료 API가 재시도되었을 때, 이전에 저장된 답변과 같은 요청인지 확인한다.
    public boolean hasSameAnswer(String normalizedAnswerText) {
        return answerText != null && answerText.equals(normalizedAnswerText);
    }
}
