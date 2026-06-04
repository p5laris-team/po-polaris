INSERT INTO prompt_templates (
    name,
    category,
    template,
    version,
    active
) VALUES (
    'mission_text_character_tone',
    'CHARACTER_TONE',
    '선택된 seed 미션의 제목, 설명, 보상, 카테고리는 변경하지 않는다. 캐릭터 말투로 characterMessage, completionQuestion, completionCharacterResponse만 생성한다. 각 문구는 짧고 죄책감이나 비난을 유발하지 않아야 한다.',
    1,
    TRUE
);
