UPDATE prompt_templates
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE category = 'CHARACTER_TONE'
  AND active = TRUE;

INSERT INTO prompt_templates (
    name,
    category,
    template,
    version,
    active
) VALUES (
    'autonomous-mission-generation',
    'MISSION_GENERATION',
    '온보딩 context, 최근 미션 이력, 완료 답변, 피드백을 바탕으로 자율 미션 후보를 JSON으로 생성한다. title과 description은 사용자가 바로 읽는 일반 한국어 문장으로 작성하고, 캐릭터 발화나 "(해석: ...)" 형식을 절대 넣지 않는다. 캐릭터 말투는 characterMessage, completionQuestion, completionCharacterResponse에만 적용한다. allowedDifficulties 안에서 missionIntensity를 목표 난이도로 우선 반영해 EASY/NORMAL/CHALLENGE를 고르고, CHALLENGE는 하루 1회 정책과 안전한 동작 범위 안에서만 사용한다. 운동 NORMAL은 저강도라도 3~5분 반복 미션으로 만들 수 있다. 보상은 AI가 결정하지 않고 mission 서버 정책이 확정한다. 금지 표현, 길이, enum, CHALLENGE 하루 1회 제한은 서버 검증을 통과해야 한다.',
    1,
    TRUE
)
ON CONFLICT (name, version) DO UPDATE
SET category = EXCLUDED.category,
    template = EXCLUDED.template,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;
