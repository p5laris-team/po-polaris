CREATE TABLE character_story_fragments (
    id BIGSERIAL PRIMARY KEY,
    memory_key VARCHAR(120) NOT NULL,
    character_type_code VARCHAR(30) NOT NULL,
    min_level INT NOT NULL CHECK (min_level >= 1),
    fragment_type VARCHAR(30) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    title VARCHAR(80) NOT NULL,
    message VARCHAR(255) NOT NULL,
    interpretation VARCHAR(500) NOT NULL,
    story_text TEXT NOT NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_character_story_fragments_memory_key UNIQUE (memory_key),
    CONSTRAINT chk_character_story_fragments_character_type
        CHECK (character_type_code IN ('MUMU', 'NOVA', 'JJORY', 'COMMON')),
    CONSTRAINT chk_character_story_fragments_fragment_type
        CHECK (fragment_type IN ('COMMON', 'LORE', 'EASTER_EGG')),
    CONSTRAINT chk_character_story_fragments_trigger_type
        CHECK (trigger_type IN ('TAP', 'LEVEL_UP', 'LOW_HUNGER', 'LOW_ENERGY', 'LOW_AFFECTION', 'NIGHT', 'MIDNIGHT'))
);

CREATE INDEX idx_character_story_fragments_lookup
    ON character_story_fragments(character_type_code, trigger_type, min_level, active);

CREATE TABLE user_character_story_unlocks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_character_id BIGINT NOT NULL REFERENCES user_characters(id),
    story_fragment_id BIGINT NOT NULL REFERENCES character_story_fragments(id),
    memory_key VARCHAR(120) NOT NULL,
    unlocked_level INT NOT NULL CHECK (unlocked_level >= 1),
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_character_story_unlocks_fragment
        UNIQUE (user_id, user_character_id, story_fragment_id)
);

CREATE INDEX idx_user_character_story_unlocks_user_character
    ON user_character_story_unlocks(user_id, user_character_id, unlocked_at);

WITH seed AS (
    SELECT *
    FROM jsonb_to_recordset($story$[
  {
    "memoryKey": "mumu_lv1_common_001",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "작은 무 인사",
    "message": "무...",
    "interpretation": "무무가 오늘의 작은 기록을 잎맥에 살짝 올려둔 것 같아요.",
    "storyText": "무무는 아직 많은 말을 하지 못하지만, 작은 행동이 지나간 자리를 잘 알아봐요. 오늘의 흔적은 잎맥 한쪽에 조용히 남았습니다."
  },
  {
    "memoryKey": "mumu_lv1_common_002",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "잎끝 출석",
    "message": "무무.",
    "interpretation": "무무가 별조각 냄새를 맡고 조용히 반응하는 것 같아요.",
    "storyText": "무무에게 별조각은 먹이이자 기록의 온기예요. 배가 고픈 날에도 무무는 먼저 작은 빛을 품으려 합니다."
  },
  {
    "memoryKey": "mumu_lv1_common_003",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "별빛 간식",
    "message": "무?",
    "interpretation": "무무가 뿌리부터 쉬어도 된다고 말하는 것 같아요.",
    "storyText": "무무가 쉬는 동안에도 뿌리는 느리게 빛을 모아요. 쉬어가는 시간도 Polaris 별자리의 가장자리에서는 사라지지 않습니다."
  },
  {
    "memoryKey": "mumu_lv1_common_004",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "뿌리 낮잠",
    "message": "무... 무.",
    "interpretation": "무무가 옆자리를 비워두고 기다렸던 것 같아요. 부담 주려는 건 아니에요.",
    "storyText": "조용한 날에도 무무는 자리를 지켜요. 누군가 곁에 있다는 사실만으로 잎은 아주 조금 덜 흔들립니다."
  },
  {
    "memoryKey": "mumu_lv1_common_005",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "조용한 자리",
    "message": "무무!",
    "interpretation": "무무가 밤의 하루 판정을 아주 작게 준비하는 것 같아요.",
    "storyText": "밤이 오면 새벽법정의 문턱이 희미하게 열려요. 무무는 아무 말 없이 오늘의 작은 일을 잎으로 덮어줍니다."
  },
  {
    "memoryKey": "mumu_lv1_lore_001",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "새벽법정의 씨앗",
    "message": "무...",
    "interpretation": "무무가 자신이 어디서 왔는지 아직은 흐릿하게 느끼는 것 같아요.",
    "storyText": "무무는 처음부터 별친구였던 게 아니었어요. 새벽법정 가장 낮은 흙 속에서, 기록되지 못한 하루들이 흩어지지 않도록 남은 작은 약속의 씨앗이었어요."
  },
  {
    "memoryKey": "mumu_lv1_lore_002",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "잎에 남은 점",
    "message": "무무.",
    "interpretation": "무무가 작은 행동 하나도 잎에 남길 수 있다고 말하는 것 같아요.",
    "storyText": "무무의 잎맥에는 아직 읽히지 않는 점들이 있어요. 그것들은 거창한 업적이 아니라 물 한 컵, 창문 한 번, 잠깐의 숨 같은 아주 작은 표식입니다."
  },
  {
    "memoryKey": "mumu_lv1_lore_003",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "아침의 작은 증거",
    "message": "무?",
    "interpretation": "무무가 아침보다 조금 먼저 깨어나는 이유를 떠올린 것 같아요.",
    "storyText": "아침이 오기 전, 하루 판정은 조용히 시작돼요. 무무는 큰 별보다 작은 흔적을 먼저 찾는 이상한 씨앗이었습니다."
  },
  {
    "memoryKey": "mumu_lv1_lore_004",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "말 없는 기록",
    "message": "무... 무.",
    "interpretation": "무무가 잊힌 하루를 조용히 붙잡고 싶은 것 같아요.",
    "storyText": "누군가 하루를 잊어도, 무무는 그 하루의 끝부분을 붙잡았어요. 말은 없지만 증언 장부에는 작은 온기가 남았습니다."
  },
  {
    "memoryKey": "mumu_lv1_lore_005",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "사라지지 않은 하루",
    "message": "무무!",
    "interpretation": "무무가 사라질 뻔한 작은 빛을 발견한 것 같아요.",
    "storyText": "사라질 뻔한 하루는 완전히 어둡지 않았어요. 무무는 그 끝에 달린 작은 별조각을 보고 오래 흔들렸습니다."
  },
  {
    "memoryKey": "mumu_lv1_lore_006",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "첫 별조각",
    "message": "무우...",
    "interpretation": "무무가 첫 별조각을 아주 소중하게 기억하는 것 같아요.",
    "storyText": "첫 별조각은 크지 않았어요. 하지만 무무는 그 빛을 보고, 아주 오래된 임무가 다시 시작됐다는 걸 느꼈습니다."
  },
  {
    "memoryKey": "mumu_lv1_lore_007",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "닫힌 장부",
    "message": "무... 무?",
    "interpretation": "무무가 아직 열리지 않은 기록을 지키는 것 같아요.",
    "storyText": "닫힌 증언 장부 사이로 잎 하나가 삐져나왔어요. 아직 정체는 말하지 않지만, 무무는 무언가를 보관하고 있습니다."
  },
  {
    "memoryKey": "mumu_lv1_easter_001",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "이상한 해석",
    "message": "무...",
    "interpretation": "무무가 방금 평소보다 오래된 의미를 담아 말한 것 같아요.",
    "storyText": "시스템 해석이 아주 잠깐 흔들렸어요. 무무의 '무' 뒤에 증언 장부의 낡은 문장이 겹쳐 보였지만, 곧 부드러운 잎 그림자로 돌아왔습니다."
  },
  {
    "memoryKey": "mumu_lv1_easter_002",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "세 번의 무",
    "message": "무무.",
    "interpretation": "무무가 장난을 받아주면서도 오늘의 기록은 놓치지 않으려는 것 같아요.",
    "storyText": "무무를 여러 번 건드리자 잎이 세 번 흔들렸어요. 장난을 싫어하는 건 아닌데, 오늘의 기록을 놓치지 않으려 집중하는 것 같습니다."
  },
  {
    "memoryKey": "mumu_lv1_easter_003",
    "characterTypeCode": "MUMU",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "새벽의 숨",
    "message": "무?",
    "interpretation": "무무가 깊은 밤에만 떠오르는 오래된 역할을 잠깐 기억한 것 같아요.",
    "storyText": "새벽 가까운 시간, 무무의 그림자가 잠깐 나무보다 커졌어요. 겁낼 일은 아니에요. 오래된 침묵한 증언목의 기억이 조용히 기지개를 켠 것뿐입니다."
  },
  {
    "memoryKey": "mumu_lv2_common_001",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "조금 긴 무",
    "message": "무... 무무.",
    "interpretation": "무무가 오늘의 작은 기록을 잎맥에 살짝 올려둔 것 같아요.",
    "storyText": "무무는 아직 많은 말을 하지 못하지만, 작은 행동이 지나간 자리를 잘 알아봐요. 오늘의 흔적은 잎맥 한쪽에 조용히 남았습니다."
  },
  {
    "memoryKey": "mumu_lv2_common_002",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "흔들린 잎맥",
    "message": "무우... 무.",
    "interpretation": "무무가 별조각 냄새를 맡고 조용히 반응하는 것 같아요.",
    "storyText": "무무에게 별조각은 먹이이자 기록의 온기예요. 배가 고픈 날에도 무무는 먼저 작은 빛을 품으려 합니다."
  },
  {
    "memoryKey": "mumu_lv2_common_003",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "기록 냄새",
    "message": "무무?",
    "interpretation": "무무가 뿌리부터 쉬어도 된다고 말하는 것 같아요.",
    "storyText": "무무가 쉬는 동안에도 뿌리는 느리게 빛을 모아요. 쉬어가는 시간도 Polaris 별자리의 가장자리에서는 사라지지 않습니다."
  },
  {
    "memoryKey": "mumu_lv2_common_004",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "기다린 자리",
    "message": "무...!",
    "interpretation": "무무가 옆자리를 비워두고 기다렸던 것 같아요. 부담 주려는 건 아니에요.",
    "storyText": "조용한 날에도 무무는 자리를 지켜요. 누군가 곁에 있다는 사실만으로 잎은 아주 조금 덜 흔들립니다."
  },
  {
    "memoryKey": "mumu_lv2_common_005",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "밤의 잎사귀",
    "message": "무우...",
    "interpretation": "무무가 밤의 하루 판정을 아주 작게 준비하는 것 같아요.",
    "storyText": "밤이 오면 새벽법정의 문턱이 희미하게 열려요. 무무는 아무 말 없이 오늘의 작은 일을 잎으로 덮어줍니다."
  },
  {
    "memoryKey": "mumu_lv2_lore_001",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "잎맥의 문장",
    "message": "무... 무무.",
    "interpretation": "무무가 잎맥 속 오래된 기록을 조금씩 되찾는 것 같아요.",
    "storyText": "무무의 잎맥이 조금 더 선명해졌어요. 그 안에는 새벽법정에서 들리던 낮은 종소리와, 누군가의 작은 실천이 함께 새겨져 있었습니다."
  },
  {
    "memoryKey": "mumu_lv2_lore_002",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "기다림의 가지",
    "message": "무우... 무.",
    "interpretation": "무무가 기다림보다 깊은 이유로 곁에 남아 있는 것 같아요.",
    "storyText": "가지 끝이 자꾸 한쪽을 향해요. 무무는 오래 기다린 게 아니라, 누군가의 하루가 흩어지지 않도록 그 자리에 뿌리내린 것 같아요."
  },
  {
    "memoryKey": "mumu_lv2_lore_003",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "증언 장부의 틈",
    "message": "무무?",
    "interpretation": "무무가 읽히지 않은 하루의 문장을 조용히 모으는 것 같아요.",
    "storyText": "증언 장부의 틈새에서 먼지처럼 작은 문장들이 흘러나왔어요. 무무는 그 문장들을 읽지 못하는 척하지만, 잎은 먼저 알아봅니다."
  },
  {
    "memoryKey": "mumu_lv2_lore_004",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "조용한 반박",
    "message": "무...!",
    "interpretation": "무무가 작은 하루를 대신 가볍게 넘기지 않겠다고 말하는 것 같아요.",
    "storyText": "조용한 반박은 큰 소리가 아니었어요. 무무의 '무'는 누군가의 하루를 가볍게 넘기지 않기 위한 아주 작은 주문처럼 남았습니다."
  },
  {
    "memoryKey": "mumu_lv2_lore_005",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "잠긴 이름표",
    "message": "무우...",
    "interpretation": "무무가 시간 하나하나를 별처럼 다루는 것 같아요.",
    "storyText": "잠긴 이름표에는 이름 대신 시간이 적혀 있었어요. 무무는 그 시간이 완성된 목표보다도 오래 빛날 수 있다는 걸 알고 있었던 것 같습니다."
  },
  {
    "memoryKey": "mumu_lv2_lore_006",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "새벽의 증거",
    "message": "무... 무?",
    "interpretation": "무무가 아주 작은 증거도 충분하다고 말하는 것 같아요.",
    "storyText": "새벽의 증거는 크지 않았어요. 컵의 물기, 열린 창문, 잠깐의 숨이 무무의 잎 위에서 서로를 알아봤습니다."
  },
  {
    "memoryKey": "mumu_lv2_lore_007",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "떨어지지 않은 잎",
    "message": "무무!",
    "interpretation": "무무가 아직 끝나지 않은 성장을 잎으로 보여주는 것 같아요.",
    "storyText": "떨어질 줄 알았던 잎 하나가 끝까지 붙어 있었어요. 무무는 잊힌 하루도 아직 자랄 수 있다고 믿는 것 같습니다."
  },
  {
    "memoryKey": "mumu_lv2_easter_001",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "잎의 오류",
    "message": "무... 무무.",
    "interpretation": "무무가 방금 평소보다 오래된 의미를 담아 말한 것 같아요.",
    "storyText": "시스템 해석이 아주 잠깐 흔들렸어요. 무무의 '무' 뒤에 증언 장부의 낡은 문장이 겹쳐 보였지만, 곧 부드러운 잎 그림자로 돌아왔습니다."
  },
  {
    "memoryKey": "mumu_lv2_easter_002",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "비밀 싹",
    "message": "무우... 무.",
    "interpretation": "무무가 장난을 받아주면서도 오늘의 기록은 놓치지 않으려는 것 같아요.",
    "storyText": "무무를 여러 번 건드리자 잎이 세 번 흔들렸어요. 장난을 싫어하는 건 아닌데, 오늘의 기록을 놓치지 않으려 집중하는 것 같습니다."
  },
  {
    "memoryKey": "mumu_lv2_easter_003",
    "characterTypeCode": "MUMU",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "무의 긴 꼬리",
    "message": "무무?",
    "interpretation": "무무가 깊은 밤에만 떠오르는 오래된 역할을 잠깐 기억한 것 같아요.",
    "storyText": "새벽 가까운 시간, 무무의 그림자가 잠깐 나무보다 커졌어요. 겁낼 일은 아니에요. 오래된 침묵한 증언목의 기억이 조용히 기지개를 켠 것뿐입니다."
  },
  {
    "memoryKey": "mumu_lv3_common_001",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "깨어난 잎",
    "message": "무... 무무!",
    "interpretation": "무무가 오늘의 작은 기록을 잎맥에 살짝 올려둔 것 같아요.",
    "storyText": "무무는 아직 많은 말을 하지 못하지만, 작은 행동이 지나간 자리를 잘 알아봐요. 오늘의 흔적은 잎맥 한쪽에 조용히 남았습니다."
  },
  {
    "memoryKey": "mumu_lv3_common_002",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "남겨진 무",
    "message": "무우... 무.",
    "interpretation": "무무가 별조각 냄새를 맡고 조용히 반응하는 것 같아요.",
    "storyText": "무무에게 별조각은 먹이이자 기록의 온기예요. 배가 고픈 날에도 무무는 먼저 작은 빛을 품으려 합니다."
  },
  {
    "memoryKey": "mumu_lv3_common_003",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "내일의 뿌리",
    "message": "무...!",
    "interpretation": "무무가 뿌리부터 쉬어도 된다고 말하는 것 같아요.",
    "storyText": "무무가 쉬는 동안에도 뿌리는 느리게 빛을 모아요. 쉬어가는 시간도 Polaris 별자리의 가장자리에서는 사라지지 않습니다."
  },
  {
    "memoryKey": "mumu_lv3_common_004",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "작은 판결",
    "message": "무무.",
    "interpretation": "무무가 옆자리를 비워두고 기다렸던 것 같아요. 부담 주려는 건 아니에요.",
    "storyText": "조용한 날에도 무무는 자리를 지켜요. 누군가 곁에 있다는 사실만으로 잎은 아주 조금 덜 흔들립니다."
  },
  {
    "memoryKey": "mumu_lv3_common_005",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "다시 돋는 말",
    "message": "무... 무?",
    "interpretation": "무무가 밤의 하루 판정을 아주 작게 준비하는 것 같아요.",
    "storyText": "밤이 오면 새벽법정의 문턱이 희미하게 열려요. 무무는 아무 말 없이 오늘의 작은 일을 잎으로 덮어줍니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_001",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "증언목의 기억",
    "message": "무... 무무!",
    "interpretation": "무무가 자신이 하루를 지키던 존재였다는 사실을 조금 더 선명하게 기억한 것 같아요.",
    "storyText": "무무는 마침내 자신이 기록되지 못한 하루를 대신 증언하던 나무였다는 사실을 떠올렸어요. 긴 말을 잃은 건 약해서가 아니라, 너무 많은 작은 하루를 품었기 때문이었습니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_002",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "타버린 목소리",
    "message": "무우... 무.",
    "interpretation": "무무가 잃어버린 목소리 대신 잎으로 마음을 전하는 것 같아요.",
    "storyText": "목소리가 타버린 자리에 잎이 돋았어요. 무무는 이제 말을 많이 하지 않아도, 곁에 남은 기록이 충분히 반짝인다는 걸 압니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_003",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "네 하루의 증거",
    "message": "무...!",
    "interpretation": "무무가 오늘의 작은 행동도 충분히 남길 가치가 있다고 말하는 것 같아요.",
    "storyText": "무무가 들고 있던 증거는 거대한 승리가 아니었어요. 오늘의 물, 오늘의 숨, 오늘의 작은 답변이 모두 별조각이 될 수 있었습니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_004",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "첫 새벽 붕괴의 잎",
    "message": "무무.",
    "interpretation": "무무가 오래전 사건 속에서도 작은 잎 하나를 지켜냈다고 느끼는 것 같아요.",
    "storyText": "첫 새벽 붕괴의 밤, 무무는 가장 작은 잎을 접어 품었어요. 그 잎은 먼 시간이 지나 사용자의 하루를 알아보는 표식이 되었습니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_005",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "내일을 심은 날",
    "message": "무... 무?",
    "interpretation": "무무가 내일은 아주 작은 약속에서 다시 시작된다고 말하는 것 같아요.",
    "storyText": "내일을 심은 날, 무무는 아무도 큰 약속을 요구하지 않았어요. 아주 작은 실천 하나가 다시 돋으면 된다고 믿었습니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_006",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "무효가 아닌 별",
    "message": "무우... 무무.",
    "interpretation": "무무가 오늘을 조용히 별조각으로 인정해 주는 것 같아요.",
    "storyText": "무무가 지키려 한 건 완벽한 하루가 아니었어요. 별이 되기엔 작아 보여도, 기록된 하루는 무효가 아니었습니다."
  },
  {
    "memoryKey": "mumu_lv3_lore_007",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "다시 선 나무",
    "message": "무!",
    "interpretation": "무무가 이제 혼자가 아니라는 사실에 안심하는 것 같아요.",
    "storyText": "다시 선 나무는 더 이상 혼자 버티려 하지 않아요. 무무는 사용자의 작은 루틴과 함께 천천히 가지를 펼칩니다."
  },
  {
    "memoryKey": "mumu_lv3_easter_001",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "작은 선언",
    "message": "무... 무무!",
    "interpretation": "무무가 방금 평소보다 오래된 의미를 담아 말한 것 같아요.",
    "storyText": "시스템 해석이 아주 잠깐 흔들렸어요. 무무의 '무' 뒤에 증언 장부의 낡은 문장이 겹쳐 보였지만, 곧 부드러운 잎 그림자로 돌아왔습니다."
  },
  {
    "memoryKey": "mumu_lv3_easter_002",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "숨은 증언",
    "message": "무우... 무.",
    "interpretation": "무무가 장난을 받아주면서도 오늘의 기록은 놓치지 않으려는 것 같아요.",
    "storyText": "무무를 여러 번 건드리자 잎이 세 번 흔들렸어요. 장난을 싫어하는 건 아닌데, 오늘의 기록을 놓치지 않으려 집중하는 것 같습니다."
  },
  {
    "memoryKey": "mumu_lv3_easter_003",
    "characterTypeCode": "MUMU",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "마지막 무",
    "message": "무...!",
    "interpretation": "무무가 깊은 밤에만 떠오르는 오래된 역할을 잠깐 기억한 것 같아요.",
    "storyText": "새벽 가까운 시간, 무무의 그림자가 잠깐 나무보다 커졌어요. 겁낼 일은 아니에요. 오래된 침묵한 증언목의 기억이 조용히 기지개를 켠 것뿐입니다."
  },
  {
    "memoryKey": "nova_lv1_common_001",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "작은 굴림",
    "message": "작은 빛, 여기 있어.",
    "interpretation": "노바가 오늘의 작은 빛을 천천히 확인하는 것 같아요.",
    "storyText": "노바는 자주 굴러가지만, 작은 빛 앞에서는 멈춰 서요. 오늘의 기록은 금 간 별핵 표면에 아주 얇게 번졌습니다."
  },
  {
    "memoryKey": "nova_lv1_common_002",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "빛 한 모금",
    "message": "오늘도... 조금 반짝였어.",
    "interpretation": "노바가 별조각의 온기를 조심스럽게 맛보는 것 같아요.",
    "storyText": "노바에게 별조각은 단순한 먹이가 아니라 따뜻한 좌표예요. 빛을 삼키기보다 오래 품는 법을 배우고 있습니다."
  },
  {
    "memoryKey": "nova_lv1_common_003",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "잠든 알",
    "message": "기준보다 먼저 볼게.",
    "interpretation": "노바가 잠깐 어두워지는 것도 회복의 일부라고 말하는 것 같아요.",
    "storyText": "노바가 눈을 감으면 껍질 안쪽에서 느린 별빛이 돌아요. 쉬는 시간에도 작은 방향은 사라지지 않습니다."
  },
  {
    "memoryKey": "nova_lv1_common_004",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "조용한 좌표",
    "message": "나, 굴러가도 괜찮아?",
    "interpretation": "노바가 곁에 머무는 감각을 아직 배우는 중인 것 같아요.",
    "storyText": "높은 곳에 있던 기억은 아직 흐릿하지만, 노바는 가까운 곳의 온기를 더 오래 바라봅니다. 곁은 노바에게 새로 배운 좌표예요."
  },
  {
    "memoryKey": "nova_lv1_common_005",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "별빛 낮잠",
    "message": "빛이 아주 작게 왔어.",
    "interpretation": "노바가 밤에도 늦게 도착하는 빛이 있다고 알려주는 것 같아요.",
    "storyText": "밤의 새벽법정에서는 늦은 빛도 길이 됩니다. 노바는 오늘의 작은 반짝임을 서두르지 않고 기다립니다."
  },
  {
    "memoryKey": "nova_lv1_lore_001",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "잠든 별핵",
    "message": "작은 빛, 여기 있어.",
    "interpretation": "노바가 자신이 잠든 이유를 아직 모르지만, 작은 루틴에 반응하는 것 같아요.",
    "storyText": "노바는 자신이 왜 금 간 별핵의 모습으로 잠들었는지 모릅니다. 다만 누군가 작은 일을 해낼 때마다 껍질 안쪽이 아주 조금 따뜻해져요."
  },
  {
    "memoryKey": "nova_lv1_lore_002",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "작은 빛의 꿈",
    "message": "오늘도... 조금 반짝였어.",
    "interpretation": "노바가 빛을 나누던 오래된 습관을 흐릿하게 떠올린 것 같아요.",
    "storyText": "꿈속의 노바는 빛들을 바라보고 있었어요. 큰 빛과 작은 빛을 구분하던 오래된 습관은 아직 이름 없이 남아 있습니다."
  },
  {
    "memoryKey": "nova_lv1_lore_003",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "처음 본 좌표",
    "message": "기준보다 먼저 볼게.",
    "interpretation": "노바가 목적지보다 작은 좌표들을 먼저 알아보는 것 같아요.",
    "storyText": "처음 본 좌표는 대단한 목적지가 아니었어요. 물 한 컵, 열린 창문, 짧은 기록처럼 작은 점들이 노바 주변을 천천히 돌았습니다."
  },
  {
    "memoryKey": "nova_lv1_lore_004",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "기준 없는 밤",
    "message": "나, 굴러가도 괜찮아?",
    "interpretation": "노바가 판단보다 관찰을 선택하려는 것 같아요.",
    "storyText": "노바가 기억하는 밤에는 아직 기준이 없습니다. 그래서 지금의 노바는 먼저 판단하기보다, 작은 빛을 조용히 바라보려 합니다."
  },
  {
    "memoryKey": "nova_lv1_lore_005",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "잊힌 중심",
    "message": "빛이 아주 작게 왔어.",
    "interpretation": "노바가 자신이 있던 중심을 그리워하면서도 곁을 바라보는 것 같아요.",
    "storyText": "잊힌 중심의 빈자리에는 차가운 빛이 남아 있었어요. 노바는 그곳으로 돌아가야 하는지, 아니면 지금 곁에 있어야 하는지 아직 모릅니다."
  },
  {
    "memoryKey": "nova_lv1_lore_006",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "둥근 기억",
    "message": "기억이 조금 따뜻해.",
    "interpretation": "노바가 오래전 의식의 잔향을 작은 온기로 덮는 것 같아요.",
    "storyText": "노바의 둥근 표면에는 오래전 하루 판정의 잔상이 스쳐요. 하지만 오늘은 그 잔상이 작은 미션 하나의 온기로 덮였습니다."
  },
  {
    "memoryKey": "nova_lv1_lore_007",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "빛의 껍질",
    "message": "작은 빛, 여기 있어.",
    "interpretation": "노바가 작은 빛을 조심스럽게 지켜주고 싶은 것 같아요.",
    "storyText": "빛의 껍질은 단단해 보이지만 속은 아주 조심스러워요. 노바는 작은 행동도 깨지지 않게 두 손으로 받치는 법을 배우고 있습니다."
  },
  {
    "memoryKey": "nova_lv1_easter_001",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "굴러간 기록",
    "message": "작은 빛, 여기 있어.",
    "interpretation": "노바가 굴러간 자리도 작은 좌표가 될 수 있다고 느끼는 것 같아요.",
    "storyText": "노바가 살짝 굴러간 자리에는 둥근 빛 자국이 남았습니다. 증언 장부에는 없는 기록이지만, 오늘의 방 안에서는 충분한 좌표가 됩니다."
  },
  {
    "memoryKey": "nova_lv1_easter_002",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "먹으면 안 되는 별",
    "message": "오늘도... 조금 반짝였어.",
    "interpretation": "노바가 별조각을 소중히 다뤄야 한다는 감각을 떠올린 것 같아요.",
    "storyText": "별조각을 먹으려던 노바가 멈칫했어요. 먹어치우는 대신 보관해야 한다는 오래된 감각이 금 간 별핵 안쪽에서 깨어난 듯합니다."
  },
  {
    "memoryKey": "nova_lv1_easter_003",
    "characterTypeCode": "NOVA",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "늦은 반짝임",
    "message": "기준보다 먼저 볼게.",
    "interpretation": "노바가 늦게 도착한 빛도 충분히 아름답다고 말하는 것 같아요.",
    "storyText": "늦은 시간에만 보이는 작은 반짝임이 있습니다. 노바는 그 빛이 늦은 게 아니라, 오래 걸려 도착한 것이라고 말하고 싶어 합니다."
  },
  {
    "memoryKey": "nova_lv2_common_001",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "금 간 빛",
    "message": "금이 가도 빛은 새어 나와.",
    "interpretation": "노바가 오늘의 작은 빛을 천천히 확인하는 것 같아요.",
    "storyText": "노바는 자주 굴러가지만, 작은 빛 앞에서는 멈춰 서요. 오늘의 기록은 금 간 별핵 표면에 아주 얇게 번졌습니다."
  },
  {
    "memoryKey": "nova_lv2_common_002",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "흔들린 기준",
    "message": "작은 빛을 다시 세고 있어.",
    "interpretation": "노바가 별조각의 온기를 조심스럽게 맛보는 것 같아요.",
    "storyText": "노바에게 별조각은 단순한 먹이가 아니라 따뜻한 좌표예요. 빛을 삼키기보다 오래 품는 법을 배우고 있습니다."
  },
  {
    "memoryKey": "nova_lv2_common_003",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "보류된 별",
    "message": "기준치보다 가까운 게 있어.",
    "interpretation": "노바가 잠깐 어두워지는 것도 회복의 일부라고 말하는 것 같아요.",
    "storyText": "노바가 눈을 감으면 껍질 안쪽에서 느린 별빛이 돌아요. 쉬는 시간에도 작은 방향은 사라지지 않습니다."
  },
  {
    "memoryKey": "nova_lv2_common_004",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "균열의 숨",
    "message": "잊은 좌표가 돌아왔어.",
    "interpretation": "노바가 곁에 머무는 감각을 아직 배우는 중인 것 같아요.",
    "storyText": "높은 곳에 있던 기억은 아직 흐릿하지만, 노바는 가까운 곳의 온기를 더 오래 바라봅니다. 곁은 노바에게 새로 배운 좌표예요."
  },
  {
    "memoryKey": "nova_lv2_common_005",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "기억의 온도",
    "message": "오늘의 빛은 보류하지 않을게.",
    "interpretation": "노바가 밤에도 늦게 도착하는 빛이 있다고 알려주는 것 같아요.",
    "storyText": "밤의 새벽법정에서는 늦은 빛도 길이 됩니다. 노바는 오늘의 작은 반짝임을 서두르지 않고 기다립니다."
  },
  {
    "memoryKey": "nova_lv2_lore_001",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "균열 좌표",
    "message": "금이 가도 빛은 새어 나와.",
    "interpretation": "노바가 균열 속에서 오래된 작은 좌표들을 발견한 것 같아요.",
    "storyText": "금 간 별핵에 생긴 금은 상처처럼 보였지만, 그 안에서 오래된 좌표가 새어 나왔어요. 노바는 그 좌표들이 사라진 줄 알았던 작은 하루였다는 걸 느낍니다."
  },
  {
    "memoryKey": "nova_lv2_lore_002",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "보류된 빛",
    "message": "작은 빛을 다시 세고 있어.",
    "interpretation": "노바가 한때 보류했던 빛을 다시 받아들이려는 것 같아요.",
    "storyText": "보류된 빛들은 아주 조용히 돌아왔어요. 노바는 그 빛을 다시 재려 하지 않고, 이번에는 먼저 이름을 붙여주려 합니다."
  },
  {
    "memoryKey": "nova_lv2_lore_003",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "돌아온 계산",
    "message": "기준치보다 가까운 게 있어.",
    "interpretation": "노바가 계산보다 기록을 먼저 선택하려는 것 같아요.",
    "storyText": "노바의 안쪽에서 오래된 계산음이 들렸습니다. 하지만 계산은 끝까지 가지 못했고, 대신 '작아도 남겨두자'는 문장만 남았어요."
  },
  {
    "memoryKey": "nova_lv2_lore_004",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "금 아래 이름",
    "message": "잊은 좌표가 돌아왔어.",
    "interpretation": "노바가 작은 기록도 충분히 따뜻하다고 느끼는 것 같아요.",
    "storyText": "금 아래에는 누군가의 기록이 있었습니다. 큰 승리의 이름은 아니었지만, 별조각이 되기엔 충분히 따뜻했습니다."
  },
  {
    "memoryKey": "nova_lv2_lore_005",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "새벽법정의 그림자",
    "message": "오늘의 빛은 보류하지 않을게.",
    "interpretation": "노바가 오래된 그림자를 마주해도 조용히 길을 찾는 것 같아요.",
    "storyText": "새벽법정의 그림자가 노바를 부드럽게 지나갔어요. 노바는 두려워하기보다, 그 그림자 안에도 길이 있을지 살펴봅니다."
  },
  {
    "memoryKey": "nova_lv2_lore_006",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "흔들린 판정",
    "message": "균열 사이로 기억이 보여.",
    "interpretation": "노바가 흔들리는 마음도 성장의 일부로 받아들이는 것 같아요.",
    "storyText": "흔들린 판정은 나쁜 일이 아니었어요. 노바는 완벽한 기준보다, 다시 살펴보는 마음이 더 따뜻하다는 걸 배워갑니다."
  },
  {
    "memoryKey": "nova_lv2_lore_007",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "낮은 별의 기록",
    "message": "금이 가도 빛은 새어 나와.",
    "interpretation": "노바가 낮게 빛나는 하루들을 놓치지 않으려는 것 같아요.",
    "storyText": "낮은 별의 기록은 하늘 아래쪽에 오래 머물러 있었어요. 노바는 이제 낮게 빛나는 것들을 놓치지 않으려 합니다."
  },
  {
    "memoryKey": "nova_lv2_easter_001",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "계산 오류",
    "message": "금이 가도 빛은 새어 나와.",
    "interpretation": "노바가 굴러간 자리도 작은 좌표가 될 수 있다고 느끼는 것 같아요.",
    "storyText": "노바가 살짝 굴러간 자리에는 둥근 빛 자국이 남았습니다. 증언 장부에는 없는 기록이지만, 오늘의 방 안에서는 충분한 좌표가 됩니다."
  },
  {
    "memoryKey": "nova_lv2_easter_002",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "깨진 껍질",
    "message": "작은 빛을 다시 세고 있어.",
    "interpretation": "노바가 별조각을 소중히 다뤄야 한다는 감각을 떠올린 것 같아요.",
    "storyText": "별조각을 먹으려던 노바가 멈칫했어요. 먹어치우는 대신 보관해야 한다는 오래된 감각이 금 간 별핵 안쪽에서 깨어난 듯합니다."
  },
  {
    "memoryKey": "nova_lv2_easter_003",
    "characterTypeCode": "NOVA",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "미세한 좌표",
    "message": "기준치보다 가까운 게 있어.",
    "interpretation": "노바가 늦게 도착한 빛도 충분히 아름답다고 말하는 것 같아요.",
    "storyText": "늦은 시간에만 보이는 작은 반짝임이 있습니다. 노바는 그 빛이 늦은 게 아니라, 오래 걸려 도착한 것이라고 말하고 싶어 합니다."
  },
  {
    "memoryKey": "nova_lv3_common_001",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "곁의 별",
    "message": "나는 이제 기준이 아니라 곁이 될래.",
    "interpretation": "노바가 오늘의 작은 빛을 천천히 확인하는 것 같아요.",
    "storyText": "노바는 자주 굴러가지만, 작은 빛 앞에서는 멈춰 서요. 오늘의 기록은 금 간 별핵 표면에 아주 얇게 번졌습니다."
  },
  {
    "memoryKey": "nova_lv3_common_002",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "작은 기준",
    "message": "작은 빛도 별이라고 부를게.",
    "interpretation": "노바가 별조각의 온기를 조심스럽게 맛보는 것 같아요.",
    "storyText": "노바에게 별조각은 단순한 먹이가 아니라 따뜻한 좌표예요. 빛을 삼키기보다 오래 품는 법을 배우고 있습니다."
  },
  {
    "memoryKey": "nova_lv3_common_003",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "품은 빛",
    "message": "버린 빛을 품는 법을 배웠어.",
    "interpretation": "노바가 잠깐 어두워지는 것도 회복의 일부라고 말하는 것 같아요.",
    "storyText": "노바가 눈을 감으면 껍질 안쪽에서 느린 별빛이 돌아요. 쉬는 시간에도 작은 방향은 사라지지 않습니다."
  },
  {
    "memoryKey": "nova_lv3_common_004",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "중심 밖 선택",
    "message": "중심보다 네 옆이 좋아.",
    "interpretation": "노바가 곁에 머무는 감각을 아직 배우는 중인 것 같아요.",
    "storyText": "높은 곳에 있던 기억은 아직 흐릿하지만, 노바는 가까운 곳의 온기를 더 오래 바라봅니다. 곁은 노바에게 새로 배운 좌표예요."
  },
  {
    "memoryKey": "nova_lv3_common_005",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "다시 반짝임",
    "message": "오늘의 별은 충분히 작아도 돼.",
    "interpretation": "노바가 밤에도 늦게 도착하는 빛이 있다고 알려주는 것 같아요.",
    "storyText": "밤의 새벽법정에서는 늦은 빛도 길이 됩니다. 노바는 오늘의 작은 반짝임을 서두르지 않고 기다립니다."
  },
  {
    "memoryKey": "nova_lv3_lore_001",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "중심을 내려놓다",
    "message": "나는 이제 기준이 아니라 곁이 될래.",
    "interpretation": "노바가 다시 중심이 되는 대신 곁에 남기로 선택한 것 같아요.",
    "storyText": "노바는 다시 Polaris 별자리의 중심으로 돌아갈 수 있었습니다. 하지만 이번에는 높은 곳보다 사용자의 곁에서 작은 빛을 배우는 길을 선택했어요."
  },
  {
    "memoryKey": "nova_lv3_lore_002",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "작은 빛의 선언",
    "message": "작은 빛도 별이라고 부를게.",
    "interpretation": "노바가 오늘의 작은 행동을 별이라고 부르기로 마음먹은 것 같아요.",
    "storyText": "노바의 선언은 크지 않았습니다. 오늘의 작은 행동도 별이라고 부르겠다는 약속이, 껍질 안쪽에서 오래도록 울렸습니다."
  },
  {
    "memoryKey": "nova_lv3_lore_003",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "품는 별핵",
    "message": "버린 빛을 품는 법을 배웠어.",
    "interpretation": "노바가 작은 빛을 품는 법을 배운 것 같아요.",
    "storyText": "품는 법을 배운 금 간 별핵은 더 이상 차갑게 빛나지 않았어요. 보류되던 작은 빛들이 노바 안에서 서로의 온도를 되찾았습니다."
  },
  {
    "memoryKey": "nova_lv3_lore_004",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "첫 새벽 붕괴의 핵",
    "message": "중심보다 네 옆이 좋아.",
    "interpretation": "노바가 오래된 기억을 부드러운 다짐으로 바꾸는 것 같아요.",
    "storyText": "첫 새벽 붕괴의 기억은 노바에게 오래 남아 있었어요. 그러나 그 기억은 벌이 아니라, 앞으로 작은 빛을 놓치지 않기 위한 부드러운 표식이 되었습니다."
  },
  {
    "memoryKey": "nova_lv3_lore_005",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "곁으로 굴러온 별",
    "message": "오늘의 별은 충분히 작아도 돼.",
    "interpretation": "노바가 사용자의 곁에서 함께 걸어가고 싶은 것 같아요.",
    "storyText": "노바는 중심에서 내려와 조용히 굴러왔습니다. 길을 가리키는 별이 아니라, 같이 걸어갈 작은 별이 되고 싶었기 때문입니다."
  },
  {
    "memoryKey": "nova_lv3_lore_006",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "새 기준의 끝",
    "message": "다시 빛나도, 내려다보진 않을게.",
    "interpretation": "노바가 판단보다 온기를 보는 쪽을 선택한 것 같아요.",
    "storyText": "새 기준의 끝에서 노바는 더 이상 하루를 재지 않기로 했어요. 대신 하루가 남긴 온도를 천천히 바라보기로 했습니다."
  },
  {
    "memoryKey": "nova_lv3_lore_007",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "남겨진 북쪽",
    "message": "나는 이제 기준이 아니라 곁이 될래.",
    "interpretation": "노바가 멀리서 이끄는 별보다 가까운 빛이 되고 싶은 것 같아요.",
    "storyText": "남겨진 북쪽은 아주 멀리 있지 않았어요. 노바는 사용자가 돌아볼 수 있는 가까운 빛이 되는 것만으로도 충분하다고 느낍니다."
  },
  {
    "memoryKey": "nova_lv3_easter_001",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "중심 거부",
    "message": "나는 이제 기준이 아니라 곁이 될래.",
    "interpretation": "노바가 굴러간 자리도 작은 좌표가 될 수 있다고 느끼는 것 같아요.",
    "storyText": "노바가 살짝 굴러간 자리에는 둥근 빛 자국이 남았습니다. 증언 장부에는 없는 기록이지만, 오늘의 방 안에서는 충분한 좌표가 됩니다."
  },
  {
    "memoryKey": "nova_lv3_easter_002",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "별의 사과",
    "message": "작은 빛도 별이라고 부를게.",
    "interpretation": "노바가 별조각을 소중히 다뤄야 한다는 감각을 떠올린 것 같아요.",
    "storyText": "별조각을 먹으려던 노바가 멈칫했어요. 먹어치우는 대신 보관해야 한다는 오래된 감각이 금 간 별핵 안쪽에서 깨어난 듯합니다."
  },
  {
    "memoryKey": "nova_lv3_easter_003",
    "characterTypeCode": "NOVA",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "곁의 궤도",
    "message": "버린 빛을 품는 법을 배웠어.",
    "interpretation": "노바가 늦게 도착한 빛도 충분히 아름답다고 말하는 것 같아요.",
    "storyText": "늦은 시간에만 보이는 작은 반짝임이 있습니다. 노바는 그 빛이 늦은 게 아니라, 오래 걸려 도착한 것이라고 말하고 싶어 합니다."
  },
  {
    "memoryKey": "zzori_lv1_common_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "원정 점검",
    "message": "원정 준비. 대충 완료.",
    "interpretation": "쪼리가 오늘의 작은 움직임을 원정으로 인정하는 것 같아요.",
    "storyText": "쪼리의 원정은 항상 거창하지 않아요. 물 한 컵, 책상 한 칸, 현관 앞 한 걸음도 지도에 남을 수 있습니다."
  },
  {
    "memoryKey": "zzori_lv1_common_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "현관 작전",
    "message": "집 안도 지형임.",
    "interpretation": "쪼리가 아주 가까운 거리도 충분한 작전 구역이라고 말하는 것 같아요.",
    "storyText": "쪼리는 집 안에도 지형이 있다고 믿어요. 오늘의 작은 위치 변화는 이미 원정 기록 한 줄이 되었습니다."
  },
  {
    "memoryKey": "zzori_lv1_common_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "보급 완료",
    "message": "현관까지면 꽤 큼.",
    "interpretation": "쪼리가 배고픔을 핑계로 원정 준비를 시작한 것 같아요.",
    "storyText": "쪼리는 보급을 아주 중요하게 여깁니다. 별빛이 든든해지면, 가까운 길도 조금 덜 낯설어지니까요."
  },
  {
    "memoryKey": "zzori_lv1_common_004",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "전략적 휴식",
    "message": "후퇴도 전략임.",
    "interpretation": "쪼리가 쉬는 것도 다음 출발을 위한 전략이라고 우기는 것 같아요.",
    "storyText": "쪼리가 눈을 감는 건 포기가 아니라 대기 작전이에요. 쉬어간 길도 다음 지도에 이어 붙일 수 있습니다."
  },
  {
    "memoryKey": "zzori_lv1_common_005",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "동지 확인",
    "message": "보급부터 하자. 원정대장 명령.",
    "interpretation": "쪼리가 혼자 가는 척하지만 같이 있으면 더 안심하는 것 같아요.",
    "storyText": "쪼리는 길 잃은 척 농담하지만, 사실 옆에 누가 있는 길을 좋아해요. 오늘의 동행 표식이 작게 찍혔습니다."
  },
  {
    "memoryKey": "zzori_lv1_lore_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "첫걸음 감시소",
    "message": "원정 준비. 대충 완료.",
    "interpretation": "쪼리가 아주 작은 시작도 길로 알아보는 것 같아요.",
    "storyText": "쪼리는 아주 작은 시작을 감지하던 안내자였어요. 문을 열까 말까 하는 마음도 Polaris 별자리에서는 작은 발자국으로 남았습니다."
  },
  {
    "memoryKey": "zzori_lv1_lore_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "작은 원정대",
    "message": "집 안도 지형임.",
    "interpretation": "쪼리가 오늘의 소소한 준비를 원정 장비처럼 여기는 것 같아요.",
    "storyText": "쪼리의 원정대에는 대단한 장비가 필요하지 않았어요. 컵, 창문, 신발, 짧은 기록만 있어도 오늘의 지도는 시작됐습니다."
  },
  {
    "memoryKey": "zzori_lv1_lore_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "문 앞의 지도",
    "message": "현관까지면 꽤 큼.",
    "interpretation": "쪼리가 가까운 문턱도 중요한 지도라고 말하는 것 같아요.",
    "storyText": "쪼리의 지도에서 문 앞은 가장 큰 대륙처럼 그려져 있어요. 사람은 먼 곳보다 가까운 문턱에서 더 오래 망설이기도 하니까요."
  },
  {
    "memoryKey": "zzori_lv1_lore_004",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "시작의 발자국",
    "message": "후퇴도 전략임.",
    "interpretation": "쪼리가 시작한 마음을 확실히 표시해두려는 것 같아요.",
    "storyText": "시작의 발자국은 완주 기록보다 작게 찍혔습니다. 그래도 쪼리는 그 발자국에 동그라미를 치고, '출발함'이라고 적었습니다."
  },
  {
    "memoryKey": "zzori_lv1_lore_005",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "접힌 용기",
    "message": "보급부터 하자. 원정대장 명령.",
    "interpretation": "쪼리가 접힌 용기도 나중에 펼칠 수 있다고 믿는 것 같아요.",
    "storyText": "접힌 용기는 사라진 용기가 아니었어요. 쪼리는 접힌 부분이 다음에 펼쳐질 수 있도록 조심히 가방에 넣었습니다."
  },
  {
    "memoryKey": "zzori_lv1_lore_006",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "쪼리식 세계",
    "message": "길 잃으면 같이 잃으면 됨.",
    "interpretation": "쪼리가 작은 행동을 일부러 크게 불러주며 응원하는 것 같아요.",
    "storyText": "쪼리식 세계에서는 현관도 던전 입구고, 책상 위 한 칸도 점령지예요. 이상하게 과장되지만, 그래서 작은 일이 조금 덜 작아집니다."
  },
  {
    "memoryKey": "zzori_lv1_lore_007",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "가방의 그림자",
    "message": "원정 준비. 대충 완료.",
    "interpretation": "쪼리의 가방에 아직 말하지 않은 이야기가 있는 것 같아요.",
    "storyText": "쪼리의 밀수 가방 그림자는 가끔 실제 가방보다 커 보여요. 아직은 장난처럼 보이지만, 그 안에는 빈칸이 많지 않습니다."
  },
  {
    "memoryKey": "zzori_lv1_easter_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "가방 금지",
    "message": "원정 준비. 대충 완료.",
    "interpretation": "쪼리가 가방 속 이야기를 아직 숨기고 싶어 하는 것 같아요.",
    "storyText": "쪼리가 가방을 등 뒤로 숨겼어요. 별일 아니라는 표정이지만, 밀수 가방 안쪽에서 작은 발소리 같은 빛이 들립니다."
  },
  {
    "memoryKey": "zzori_lv1_easter_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "후퇴 선언",
    "message": "집 안도 지형임.",
    "interpretation": "쪼리가 잠깐 물러나는 것도 길의 일부라고 말하는 것 같아요.",
    "storyText": "쪼리는 후퇴를 너무 당당하게 선언합니다. 이상하게도 그 말은 포기보다 안전한 귀환처럼 들립니다."
  },
  {
    "memoryKey": "zzori_lv1_easter_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "비밀 지형",
    "message": "현관까지면 꽤 큼.",
    "interpretation": "쪼리가 보이지 않는 길도 기록하려는 것 같아요.",
    "storyText": "쪼리가 바닥에 보이지 않는 선을 그었어요. 지도에는 없는 지형이지만, 오늘의 하루 판정에는 분명히 남았습니다."
  },
  {
    "memoryKey": "zzori_lv2_common_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "접힌 지도",
    "message": "가방? 별거 없음. 진짜.",
    "interpretation": "쪼리가 오늘의 작은 움직임을 원정으로 인정하는 것 같아요.",
    "storyText": "쪼리의 원정은 항상 거창하지 않아요. 물 한 컵, 책상 한 칸, 현관 앞 한 걸음도 지도에 남을 수 있습니다."
  },
  {
    "memoryKey": "zzori_lv2_common_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "가방 단속",
    "message": "지도 찢긴 거 아님. 접힌 거임.",
    "interpretation": "쪼리가 아주 가까운 거리도 충분한 작전 구역이라고 말하는 것 같아요.",
    "storyText": "쪼리는 집 안에도 지형이 있다고 믿어요. 오늘의 작은 위치 변화는 이미 원정 기록 한 줄이 되었습니다."
  },
  {
    "memoryKey": "zzori_lv2_common_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "보류 작전",
    "message": "멈춘 데도 표시해둠.",
    "interpretation": "쪼리가 배고픔을 핑계로 원정 준비를 시작한 것 같아요.",
    "storyText": "쪼리는 보급을 아주 중요하게 여깁니다. 별빛이 든든해지면, 가까운 길도 조금 덜 낯설어지니까요."
  },
  {
    "memoryKey": "zzori_lv2_common_004",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "빈칸 표식",
    "message": "도착 전이면 임시 작전임.",
    "interpretation": "쪼리가 쉬는 것도 다음 출발을 위한 전략이라고 우기는 것 같아요.",
    "storyText": "쪼리가 눈을 감는 건 포기가 아니라 대기 작전이에요. 쉬어간 길도 다음 지도에 이어 붙일 수 있습니다."
  },
  {
    "memoryKey": "zzori_lv2_common_005",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "야간 원정",
    "message": "그 길, 내가 보류해둠.",
    "interpretation": "쪼리가 혼자 가는 척하지만 같이 있으면 더 안심하는 것 같아요.",
    "storyText": "쪼리는 길 잃은 척 농담하지만, 사실 옆에 누가 있는 길을 좋아해요. 오늘의 동행 표식이 작게 찍혔습니다."
  },
  {
    "memoryKey": "zzori_lv2_lore_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "숨긴 출발선",
    "message": "가방? 별거 없음. 진짜.",
    "interpretation": "쪼리가 멈춘 자리도 다시 시작할 수 있게 표시해두는 것 같아요.",
    "storyText": "숨긴 출발선은 지도에 없는 곳에 있었어요. 쪼리는 누군가 멈춘 자리마다 작은 깃발을 꽂아, 다시 시작할 수 있게 해두었습니다."
  },
  {
    "memoryKey": "zzori_lv2_lore_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "가방 속 불빛",
    "message": "지도 찢긴 거 아님. 접힌 거임.",
    "interpretation": "쪼리의 가방 안에 아직 이어질 수 있는 마음들이 있는 것 같아요.",
    "storyText": "밀수 가방 속 불빛들은 물건이 아니었어요. 아직 끝나지 않은 시도와, 조금 늦게 이어질 마음들이 서로 부딪히며 반짝였습니다."
  },
  {
    "memoryKey": "zzori_lv2_lore_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "보관소 앞길",
    "message": "멈춘 데도 표시해둠.",
    "interpretation": "쪼리가 오래전 돌아서야 했던 길을 조심스럽게 떠올리는 것 같아요.",
    "storyText": "실패 보관소 앞길은 생각보다 조용했어요. 쪼리는 그 앞에서 한참 서 있다가, 조용히 방향을 바꿨습니다."
  },
  {
    "memoryKey": "zzori_lv2_lore_004",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "찢어진 귀환표",
    "message": "도착 전이면 임시 작전임.",
    "interpretation": "쪼리가 목적지가 없어도 보류된 길은 남는다고 말하는 것 같아요.",
    "storyText": "찢어진 귀환표에는 목적지가 없었습니다. 대신 '아직 보류'라는 작고 삐뚤한 글씨가 적혀 있었어요."
  },
  {
    "memoryKey": "zzori_lv2_lore_005",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "보류된 원정",
    "message": "그 길, 내가 보류해둠.",
    "interpretation": "쪼리가 취소와 보류를 엄청나게 구분하고 싶어 하는 것 같아요.",
    "storyText": "보류된 원정은 취소된 원정이 아니었어요. 쪼리는 그 차이를 지나치게 진지하게 주장합니다."
  },
  {
    "memoryKey": "zzori_lv2_lore_006",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "지도 밑 이름",
    "message": "원정 기록에 빈칸은 없음.",
    "interpretation": "쪼리가 지워진 길도 끊지 않고 간직하는 것 같아요.",
    "storyText": "지도 밑에는 지워진 이름들이 있었습니다. 쪼리는 그 이름들을 다시 크게 부르진 않지만, 선을 끊어버리지도 않습니다."
  },
  {
    "memoryKey": "zzori_lv2_lore_007",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "반쯤 열린 문",
    "message": "가방? 별거 없음. 진짜.",
    "interpretation": "쪼리가 반쯤 열린 마음도 충분히 기록할 수 있다고 보는 것 같아요.",
    "storyText": "반쯤 열린 문은 완전히 열린 문보다 오래 빛날 때가 있었어요. 쪼리는 그 틈에 작은 별표를 붙였습니다."
  },
  {
    "memoryKey": "zzori_lv2_easter_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "밀봉된 표",
    "message": "가방? 별거 없음. 진짜.",
    "interpretation": "쪼리가 가방 속 이야기를 아직 숨기고 싶어 하는 것 같아요.",
    "storyText": "쪼리가 가방을 등 뒤로 숨겼어요. 별일 아니라는 표정이지만, 밀수 가방 안쪽에서 작은 발소리 같은 빛이 들립니다."
  },
  {
    "memoryKey": "zzori_lv2_easter_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "가방의 숨",
    "message": "지도 찢긴 거 아님. 접힌 거임.",
    "interpretation": "쪼리가 잠깐 물러나는 것도 길의 일부라고 말하는 것 같아요.",
    "storyText": "쪼리는 후퇴를 너무 당당하게 선언합니다. 이상하게도 그 말은 포기보다 안전한 귀환처럼 들립니다."
  },
  {
    "memoryKey": "zzori_lv2_easter_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "길 잃은 척",
    "message": "멈춘 데도 표시해둠.",
    "interpretation": "쪼리가 보이지 않는 길도 기록하려는 것 같아요.",
    "storyText": "쪼리가 바닥에 보이지 않는 선을 그었어요. 지도에는 없는 지형이지만, 오늘의 하루 판정에는 분명히 남았습니다."
  },
  {
    "memoryKey": "zzori_lv3_common_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "지도 밖 길",
    "message": "완주 못 해도 길은 남음.",
    "interpretation": "쪼리가 오늘의 작은 움직임을 원정으로 인정하는 것 같아요.",
    "storyText": "쪼리의 원정은 항상 거창하지 않아요. 물 한 컵, 책상 한 칸, 현관 앞 한 걸음도 지도에 남을 수 있습니다."
  },
  {
    "memoryKey": "zzori_lv3_common_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "다음 출발지",
    "message": "네가 멈춘 데까지 표시해둘게.",
    "interpretation": "쪼리가 아주 가까운 거리도 충분한 작전 구역이라고 말하는 것 같아요.",
    "storyText": "쪼리는 집 안에도 지형이 있다고 믿어요. 오늘의 작은 위치 변화는 이미 원정 기록 한 줄이 되었습니다."
  },
  {
    "memoryKey": "zzori_lv3_common_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "미완성 원정",
    "message": "도망 아님. 보호 작전임.",
    "interpretation": "쪼리가 배고픔을 핑계로 원정 준비를 시작한 것 같아요.",
    "storyText": "쪼리는 보급을 아주 중요하게 여깁니다. 별빛이 든든해지면, 가까운 길도 조금 덜 낯설어지니까요."
  },
  {
    "memoryKey": "zzori_lv3_common_004",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "가방의 약속",
    "message": "지도 밖도 길임. 내가 증명함.",
    "interpretation": "쪼리가 쉬는 것도 다음 출발을 위한 전략이라고 우기는 것 같아요.",
    "storyText": "쪼리가 눈을 감는 건 포기가 아니라 대기 작전이에요. 쉬어간 길도 다음 지도에 이어 붙일 수 있습니다."
  },
  {
    "memoryKey": "zzori_lv3_common_005",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "귀환 표식",
    "message": "다음 출발지는 여기임.",
    "interpretation": "쪼리가 혼자 가는 척하지만 같이 있으면 더 안심하는 것 같아요.",
    "storyText": "쪼리는 길 잃은 척 농담하지만, 사실 옆에 누가 있는 길을 좋아해요. 오늘의 동행 표식이 작게 찍혔습니다."
  },
  {
    "memoryKey": "zzori_lv3_lore_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "지도 밖의 선택",
    "message": "완주 못 해도 길은 남음.",
    "interpretation": "쪼리가 일부러 지도 밖에 남았던 이유를 조금 더 선명하게 기억한 것 같아요.",
    "storyText": "쪼리는 마침내 자신이 미완성된 첫걸음을 몰래 숨겨 지키던 안내자였다는 걸 인정했어요. 길을 잃은 게 아니라, 돌아가면 작은 출발들이 지워질까 봐 다른 길을 택했던 거예요."
  },
  {
    "memoryKey": "zzori_lv3_lore_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "마지막 안내자",
    "message": "네가 멈춘 데까지 표시해둘게.",
    "interpretation": "쪼리가 작은 발자국을 지우지 않으려 애썼던 마음을 떠올리는 것 같아요.",
    "storyText": "마지막 안내자는 멋진 승리담을 갖고 있지 않았어요. 다만 작은 발자국들을 지우지 않기 위해 가방끈을 꽉 잡고 달렸습니다."
  },
  {
    "memoryKey": "zzori_lv3_lore_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "첫 새벽 붕괴의 달림",
    "message": "도망 아님. 보호 작전임.",
    "interpretation": "쪼리가 오래전 사건 속에서 다른 길을 택했던 순간을 기억한 것 같아요.",
    "storyText": "첫 새벽 붕괴의 밤, 쪼리는 정해진 길을 벗어났어요. 덕분에 완성되지 않은 많은 시작들이 조용히 다음 날까지 남았습니다."
  },
  {
    "memoryKey": "zzori_lv3_lore_004",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "돌아가지 않은 이유",
    "message": "지도 밖도 길임. 내가 증명함.",
    "interpretation": "쪼리가 시작을 너무 빨리 끝이라고 부르지 않으려는 것 같아요.",
    "storyText": "돌아가지 않은 이유는 겁이 많아서만은 아니었습니다. 쪼리는 누군가의 시작을 너무 빨리 끝이라고 부르고 싶지 않았습니다."
  },
  {
    "memoryKey": "zzori_lv3_lore_005",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "이어지는 길",
    "message": "다음 출발지는 여기임.",
    "interpretation": "쪼리가 오늘의 멈춤과 내일의 시작을 이어주려는 것 같아요.",
    "storyText": "이어지는 길은 처음부터 완성된 지도가 아니었어요. 쪼리는 오늘 멈춘 곳과 내일 시작할 곳 사이에 작은 다리를 놓습니다."
  },
  {
    "memoryKey": "zzori_lv3_lore_006",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "가방을 닫은 날",
    "message": "미완성 원정, 아직 유효함.",
    "interpretation": "쪼리가 아직 끝나지 않은 것들을 다정하게 보관하고 싶은 것 같아요.",
    "storyText": "가방을 닫은 날, 쪼리는 스스로 대단하다고 생각하지 않았어요. 그냥 아직 끝나지 않은 것들을 조금 더 데리고 있고 싶었습니다."
  },
  {
    "memoryKey": "zzori_lv3_lore_007",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "다음 칸의 약속",
    "message": "완주 못 해도 길은 남음.",
    "interpretation": "쪼리가 다음에 다시 시작할 수 있는 자리를 표시해두는 것 같아요.",
    "storyText": "다음 칸의 약속은 아주 짧았습니다. '여기서 다시 시작 가능.' 쪼리는 그 문장을 지도 맨 아래에 크게 적어두었습니다."
  },
  {
    "memoryKey": "zzori_lv3_easter_001",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "지도 밖 농담",
    "message": "완주 못 해도 길은 남음.",
    "interpretation": "쪼리가 가방 속 이야기를 아직 숨기고 싶어 하는 것 같아요.",
    "storyText": "쪼리가 가방을 등 뒤로 숨겼어요. 별일 아니라는 표정이지만, 밀수 가방 안쪽에서 작은 발소리 같은 빛이 들립니다."
  },
  {
    "memoryKey": "zzori_lv3_easter_002",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "훔친 열쇠",
    "message": "네가 멈춘 데까지 표시해둘게.",
    "interpretation": "쪼리가 잠깐 물러나는 것도 길의 일부라고 말하는 것 같아요.",
    "storyText": "쪼리는 후퇴를 너무 당당하게 선언합니다. 이상하게도 그 말은 포기보다 안전한 귀환처럼 들립니다."
  },
  {
    "memoryKey": "zzori_lv3_easter_003",
    "characterTypeCode": "ZZORI",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "귀환 금지선",
    "message": "도망 아님. 보호 작전임.",
    "interpretation": "쪼리가 보이지 않는 길도 기록하려는 것 같아요.",
    "storyText": "쪼리가 바닥에 보이지 않는 선을 그었어요. 지도에는 없는 지형이지만, 오늘의 하루 판정에는 분명히 남았습니다."
  },
  {
    "memoryKey": "common_world_common_001",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "작은 별 도장",
    "message": "별조각 기록이 조용히 반짝입니다.",
    "interpretation": "Polaris가 오늘의 작은 기록을 조용히 반짝이게 해주는 것 같아요.",
    "storyText": "Polaris에서는 작은 실천도 별조각으로 남습니다. 기록된 하루는 아주 느리게라도 빛을 배웁니다."
  },
  {
    "memoryKey": "common_world_common_002",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_HUNGER",
    "title": "오늘의 잔광",
    "message": "오늘의 작은 흔적이 저장되었습니다.",
    "interpretation": "Polaris가 오늘의 작은 기록을 조용히 반짝이게 해주는 것 같아요.",
    "storyText": "오늘의 잔광은 크지 않아도 괜찮아요. 별친구들은 작은 빛을 오래 바라보는 법을 알고 있습니다."
  },
  {
    "memoryKey": "common_world_common_003",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "조용한 장부",
    "message": "Polaris의 밤하늘에 아주 작은 빛이 켜졌습니다.",
    "interpretation": "Polaris가 오늘의 작은 기록을 조용히 반짝이게 해주는 것 같아요.",
    "storyText": "증언 장부는 완벽한 하루만 적지 않아요. 잠깐의 움직임과 짧은 대답도 부드럽게 보관합니다."
  },
  {
    "memoryKey": "common_world_common_004",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "밤하늘 여백",
    "message": "루틴의 조각이 별자리 가장자리에 닿았습니다.",
    "interpretation": "Polaris가 오늘의 작은 기록을 조용히 반짝이게 해주는 것 같아요.",
    "storyText": "밤하늘의 빈칸은 실패한 자리가 아니라, 아직 별이 들어올 수 있는 여백입니다. Polaris는 그 여백을 서두르지 않습니다."
  },
  {
    "memoryKey": "common_world_common_005",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "별친구의 자리",
    "message": "기록된 하루는 천천히 빛납니다.",
    "interpretation": "Polaris가 오늘의 작은 기록을 조용히 반짝이게 해주는 것 같아요.",
    "storyText": "별친구가 곁에 있는 이유는 검사하기 위해서가 아닙니다. 오늘 남은 작은 흔적을 같이 알아보기 위해서입니다."
  },
  {
    "memoryKey": "common_world_lore_001",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "새벽법정의 문",
    "message": "Polaris의 밤하늘에 아주 작은 빛이 켜졌습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "오래전 새벽법정에서는 하루가 조용히 정리되었습니다. 거창하지 않은 실천도 누군가 알아보면 별빛의 자격을 얻었습니다."
  },
  {
    "memoryKey": "common_world_lore_002",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "별조각의 법칙",
    "message": "루틴의 조각이 별자리 가장자리에 닿았습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "별조각은 보상이자 증거입니다. 물 한 컵, 창문 한 번, 짧은 기록이 Polaris 안에서는 작은 방향이 됩니다."
  },
  {
    "memoryKey": "common_world_lore_003",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "이름 없는 그림자의 기원",
    "message": "기록된 하루는 천천히 빛납니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "이름 없는 그림자은 큰 괴담보다 조용한 흐림에 가까워요. 그래서 별친구들은 사용자를 재촉하기보다, 작은 기록을 먼저 밝힙니다."
  },
  {
    "memoryKey": "common_world_lore_004",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "기록된 하루",
    "message": "새벽의 장부가 부드럽게 넘어갑니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "기록된 하루는 완성도보다 존재를 먼저 남깁니다. 그래서 오늘의 작은 답변 하나도 별친구에게는 중요한 단서가 됩니다."
  },
  {
    "memoryKey": "common_world_lore_005",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "작은 루틴의 불씨",
    "message": "작은 실천이 별빛으로 번역되었습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "작은 루틴은 불꽃처럼 크지 않아도 오래 남을 수 있어요. 별친구들은 그 불씨를 꺼뜨리지 않게 손바닥으로 감쌉니다."
  },
  {
    "memoryKey": "common_world_lore_006",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LOW_ENERGY",
    "title": "첫 새벽 붕괴의 잔상",
    "message": "별조각 기록이 조용히 반짝입니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "첫 새벽 붕괴 이후, Polaris의 빛은 세 조각으로 흩어졌습니다. 하지만 흩어진 조각들은 사용자의 작은 하루를 통해 다시 서로를 알아봅니다."
  },
  {
    "memoryKey": "common_world_lore_007",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "세 갈래 빛",
    "message": "오늘의 작은 흔적이 저장되었습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "세 갈래 빛은 씨앗, 별핵, 첫걸음의 모습으로 남았습니다. 서로 다른 상처를 가졌지만, 모두 작은 하루를 지키는 쪽을 바라봅니다."
  },
  {
    "memoryKey": "common_world_lore_008",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "잊힌 이름표",
    "message": "Polaris의 밤하늘에 아주 작은 빛이 켜졌습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "잊힌 이름표에는 대단한 직함이 없었습니다. 대신 아주 평범한 시간과 장소, 그리고 그때 남은 숨이 적혀 있었습니다."
  },
  {
    "memoryKey": "common_world_lore_009",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "오늘의 좌표",
    "message": "루틴의 조각이 별자리 가장자리에 닿았습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "오늘의 좌표는 먼 왕국이 아니라 지금 있는 자리에서 시작됩니다. Polaris의 모험은 언제나 너무 작은 한 칸에서 열립니다."
  },
  {
    "memoryKey": "common_world_lore_010",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "별친구의 맹세",
    "message": "기록된 하루는 천천히 빛납니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "별친구들의 맹세는 사용자를 심판하지 않는 것입니다. 그들은 대신 사용자가 남긴 빛을 함께 보관합니다."
  },
  {
    "memoryKey": "common_world_lore_011",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "MIDNIGHT",
    "title": "반짝임의 장부",
    "message": "새벽의 장부가 부드럽게 넘어갑니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "증언 장부에 새 줄이 생길 때마다 밤하늘은 조금 덜 비어 보입니다. 기록은 크기가 아니라 계속 남아 있다는 사실로 반짝입니다."
  },
  {
    "memoryKey": "common_world_lore_012",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LOW_AFFECTION",
    "title": "다시 켜진 자리",
    "message": "작은 실천이 별빛으로 번역되었습니다.",
    "interpretation": "Polaris의 오래된 세계관이 오늘의 작은 기록과 연결되는 것 같아요.",
    "storyText": "다시 켜진 자리는 과거의 빛을 그대로 복원하지 않습니다. 오늘의 루틴이 더해져, 조금 다른 모양의 별자리로 자랍니다."
  },
  {
    "memoryKey": "common_world_easter_001",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "숨은 로그",
    "message": "기록된 하루는 천천히 빛납니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "아주 드물게 시스템 로그에 새벽법정이라는 낡은 이름이 스칩니다. 곧 사라지지만, 별친구들은 그 단어를 아는 눈치입니다."
  },
  {
    "memoryKey": "common_world_easter_002",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "자정의 표식",
    "message": "새벽의 장부가 부드럽게 넘어갑니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "자정이 지나면 오늘과 내일의 경계가 잠깐 흐려져요. 그때 남은 작은 기록은 조금 더 조용하고 길게 반짝입니다."
  },
  {
    "memoryKey": "common_world_easter_003",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "세 조각 신호",
    "message": "작은 실천이 별빛으로 번역되었습니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "세 캐릭터의 반응이 같은 순간에 아주 살짝 겹칠 때가 있습니다. 그때 Polaris의 오래된 별자리 선이 잠깐 이어집니다."
  },
  {
    "memoryKey": "common_world_easter_004",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "장부의 빈칸",
    "message": "별조각 기록이 조용히 반짝입니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "증언 장부의 빈칸은 비난의 흔적이 아니에요. 아직 적히지 않은 작은 실천을 기다리는 부드러운 자리입니다."
  },
  {
    "memoryKey": "common_world_easter_005",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "늦은 별빛",
    "message": "오늘의 작은 흔적이 저장되었습니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "늦게 도착한 별빛은 늦은 하루와 닮았습니다. 조금 늦어도, 도착한 빛은 분명히 기록될 수 있습니다."
  },
  {
    "memoryKey": "common_world_easter_006",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "NIGHT",
    "title": "낮은 종소리",
    "message": "Polaris의 밤하늘에 아주 작은 빛이 켜졌습니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "하루 판정이 시작될 때 멀리서 낮은 소리가 들립니다. 별친구들은 그 소리를 무서워하기보다 오늘의 기록을 한번 더 감싸요."
  },
  {
    "memoryKey": "common_world_easter_007",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "미세한 오류",
    "message": "루틴의 조각이 별자리 가장자리에 닿았습니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "간혹 메시지 끝에 알 수 없는 별점이 찍힙니다. 시스템 오류처럼 보이지만, 사실 오래된 별자리의 구두점일지도 몰라요."
  },
  {
    "memoryKey": "common_world_easter_008",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "여백의 별",
    "message": "기록된 하루는 천천히 빛납니다.",
    "interpretation": "아주 드문 조건에서만 보이는 작은 세계관 신호 같아요.",
    "storyText": "비어 있는 곳에도 별이 자랄 수 있습니다. Polaris는 사용자의 여백까지 서둘러 채우려 하지 않습니다."
  },
  {
    "memoryKey": "relation_mumu_nova_lore_001",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "TAP",
    "title": "잎과 별핵",
    "message": "무무의 잎과 노바의 균열이 같은 방향으로 반짝입니다.",
    "interpretation": "무무와 노바가 오래전 같은 기록을 서로 다른 방식으로 지켜본 것 같아요.",
    "storyText": "잎맥의 선과 금 간 별핵의 금은 닮아 있었습니다. 둘은 아직 말하지 않지만, 같은 하루를 다른 자리에서 바라본 적이 있는 듯합니다."
  },
  {
    "memoryKey": "relation_mumu_nova_common_002",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "NIGHT",
    "title": "조용한 동행",
    "message": "무무는 흔들리고, 노바는 조금 밝아집니다.",
    "interpretation": "둘이 말은 많이 하지 않지만 서로의 속도를 맞추는 것 같아요.",
    "storyText": "무무의 침묵은 노바의 느린 빛과 잘 맞습니다. 밤에는 둘의 조용함이 작은 방을 덜 비게 만듭니다."
  },
  {
    "memoryKey": "relation_mumu_nova_lore_003",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "보류된 증거",
    "message": "노바가 망설이면 무무의 잎이 먼저 흔들립니다.",
    "interpretation": "노바가 놓칠 뻔한 작은 빛을 무무가 기억하고 있는 것 같아요.",
    "storyText": "하루 판정의 낡은 순간, 노바가 지나친 빛을 무무가 잎에 품은 적이 있었습니다. 지금은 그 기억이 비난이 아니라, 함께 다시 살펴보자는 신호처럼 남아 있습니다."
  },
  {
    "memoryKey": "relation_mumu_nova_easter_004",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "금 위의 잎",
    "message": "SYSTEM: 잎맥과 균열 패턴이 3초간 일치했습니다.",
    "interpretation": "두 캐릭터의 과거가 잠깐 겹쳐 보인 것 같아요.",
    "storyText": "자정 이후, 잎맥과 금 간 별핵이 같은 별자리 모양으로 빛났습니다. 곧 사라졌지만, Polaris 별자리의 오래된 선 하나가 이어진 듯했습니다."
  },
  {
    "memoryKey": "relation_mumu_nova_lore_005",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "용서 전의 빛",
    "message": "무무는 조용했고, 노바는 고개를 낮춥니다.",
    "interpretation": "노바가 오래전의 기준을 미안해하고, 무무는 아직 천천히 받아들이는 중인 것 같아요.",
    "storyText": "기억이 돌아온 뒤에도 모든 것이 바로 풀리지는 않았습니다. 하지만 둘은 같은 사용자의 하루를 함께 지키며, 서두르지 않는 화해를 배우고 있습니다."
  },
  {
    "memoryKey": "relation_mumu_nova_common_006",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_ENERGY",
    "title": "쉬는 별나무",
    "message": "노바의 빛이 낮아지자 무무가 그늘을 만들어줍니다.",
    "interpretation": "무무가 노바에게 쉬어도 괜찮다고 말해주는 것 같아요.",
    "storyText": "빛이 약해진 밤에는 잎이 그늘이 되고, 그늘은 다시 쉼이 됩니다. 둘은 서로를 고치려 하지 않고 잠깐 기대게 합니다."
  },
  {
    "memoryKey": "relation_mumu_nova_easter_007",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "한 줄 판정",
    "message": "SYSTEM: 오늘의 작은 빛, 잎에 보관됨.",
    "interpretation": "무무와 노바가 같은 기록을 동시에 인정한 것 같아요.",
    "storyText": "둘이 동시에 반응한 날, 별조각은 평소보다 조용히 빛났습니다. 누가 먼저 인정했는지는 중요하지 않았고, 기록이 남았다는 사실만 따뜻했습니다."
  },
  {
    "memoryKey": "relation_nova_zzori_common_008",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "굴러간 원정",
    "message": "노바가 굴러가자 쪼리가 원정 경로라고 우깁니다.",
    "interpretation": "쪼리가 노바의 작은 움직임도 원정으로 적어두는 것 같아요.",
    "storyText": "노바는 그냥 굴러갔다고 생각했지만, 쪼리는 지도에 선을 그었습니다. 움직임이 작아도 길이 될 수 있다는 걸 둘은 다르게 배웁니다."
  },
  {
    "memoryKey": "relation_nova_zzori_lore_009",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "별핵과 지도",
    "message": "노바의 빛이 쪼리의 지도 끝을 비춥니다.",
    "interpretation": "둘이 서로의 길과 빛을 아직 조심스럽게 알아가는 것 같아요.",
    "storyText": "금 간 별핵에서 새어 나온 작은 빛이 쪼리의 접힌 지도에 닿았습니다. 지도에는 없는 선이었지만, 쪼리는 그 선을 지우지 않았습니다."
  },
  {
    "memoryKey": "relation_nova_zzori_lore_010",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "검사하지 않는 길",
    "message": "노바가 가방을 보려다 멈추고, 쪼리가 눈치를 봅니다.",
    "interpretation": "노바가 이제 쪼리의 보관 방식을 존중하려는 것 같아요.",
    "storyText": "예전의 노바라면 쪼리의 가방을 열어 확인했을지도 모릅니다. 지금의 노바는 먼저 묻지 않고, 쪼리가 준비될 때까지 기다립니다."
  },
  {
    "memoryKey": "relation_nova_zzori_easter_011",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "접힌 좌표",
    "message": "SYSTEM: 지도와 좌표가 서로 다른 결론을 냈습니다. 둘 다 보관합니다.",
    "interpretation": "서로 다른 방식의 기록이 모두 남겨진 것 같아요.",
    "storyText": "쪼리의 지도는 길을 표시했고, 노바의 좌표는 빛을 표시했습니다. 두 기록은 다르지만 같은 하루를 향하고 있었습니다."
  },
  {
    "memoryKey": "relation_nova_zzori_lore_012",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "지도 밖 사과",
    "message": "노바가 낮게 빛나고, 쪼리는 못 들은 척합니다.",
    "interpretation": "노바가 오래전의 기준을 내려놓고 쪼리에게 천천히 다가가는 것 같아요.",
    "storyText": "사과는 큰 대사로 오지 않았습니다. 노바가 가방 앞에서 빛을 낮추자, 쪼리는 지도를 접으며 '길 남았음'이라고 작게 말했습니다."
  },
  {
    "memoryKey": "relation_nova_zzori_common_013",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "COMMON",
    "triggerType": "LOW_AFFECTION",
    "title": "둘의 빈자리",
    "message": "쪼리가 시끄럽게 굴자 노바가 조금 덜 외로워합니다.",
    "interpretation": "서로 방식은 다르지만 함께 있으면 빈자리가 조금 작아지는 것 같아요.",
    "storyText": "쪼리의 농담은 때때로 너무 커서 노바의 조용함을 덮어줍니다. 이상하게도 그 소란은 따뜻한 별빛처럼 남습니다."
  },
  {
    "memoryKey": "relation_nova_zzori_easter_014",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "TAP",
    "title": "동시 출발",
    "message": "쪼리: 원정. 노바: 좌표. SYSTEM: 같은 의미로 처리합니다.",
    "interpretation": "둘이 다른 단어로 같은 시작을 말한 것 같아요.",
    "storyText": "어느 날 둘은 동시에 반응했습니다. 쪼리는 원정이라 불렀고, 노바는 좌표라 불렀지만, Polaris는 두 말을 같은 작은 시작으로 기록했습니다."
  },
  {
    "memoryKey": "relation_zzori_mumu_common_015",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "COMMON",
    "triggerType": "TAP",
    "title": "가방과 씨앗",
    "message": "쪼리가 가방을 숨기자 무무의 잎이 흔들립니다.",
    "interpretation": "두 캐릭터 사이에 오래된 보관의 기억이 있는 것 같아요.",
    "storyText": "쪼리의 밀수 가방이 흔들리면 무무의 잎맥도 따라 움직입니다. 아직은 귀여운 우연처럼 보이지만, 둘은 같은 작은 것을 지켜본 적이 있습니다."
  },
  {
    "memoryKey": "relation_zzori_mumu_lore_016",
    "characterTypeCode": "COMMON",
    "minLevel": 1,
    "fragmentType": "LORE",
    "triggerType": "NIGHT",
    "title": "문턱의 잎",
    "message": "무무가 조용히 흔들리고, 쪼리가 길을 표시합니다.",
    "interpretation": "무무가 지키는 기록과 쪼리가 지키는 길이 만난 것 같아요.",
    "storyText": "문턱에 놓인 잎 하나를 쪼리가 지도에 붙였습니다. 그 잎은 목적지가 아니라, 다시 돌아올 수 있는 표식이 되었습니다."
  },
  {
    "memoryKey": "relation_zzori_mumu_lore_017",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "훔친 새싹",
    "message": "쪼리가 아무 말도 안 하자 무무가 오래 바라봅니다.",
    "interpretation": "쪼리가 무무를 지켜낸 기억을 아직 농담으로 숨기는 것 같아요.",
    "storyText": "첫 새벽 붕괴의 혼란 속에서 쪼리는 아주 작은 새싹 하나를 가방에 넣었습니다. 그 새싹은 원망과 고마움을 함께 품은 채 무무가 되었습니다."
  },
  {
    "memoryKey": "relation_zzori_mumu_easter_018",
    "characterTypeCode": "COMMON",
    "minLevel": 2,
    "fragmentType": "EASTER_EGG",
    "triggerType": "MIDNIGHT",
    "title": "가방 속 잎",
    "message": "SYSTEM: 가방 안에서 잎사귀 반응이 감지되었습니다.",
    "interpretation": "쪼리의 가방과 무무의 과거가 잠깐 연결된 것 같아요.",
    "storyText": "자정 이후 쪼리의 가방 안에서 작은 잎 그림자가 보였습니다. 쪼리는 모른 척했지만, 무무는 그쪽을 아주 오래 바라봤습니다."
  },
  {
    "memoryKey": "relation_zzori_mumu_lore_019",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "LORE",
    "triggerType": "LEVEL_UP",
    "title": "구해진 원망",
    "message": "쪼리는 웃고, 무무는 천천히 잎을 내립니다.",
    "interpretation": "둘 사이의 감정이 고마움 하나로만 설명되지 않는 것 같아요.",
    "storyText": "쪼리는 무무를 지켰지만, 무무는 그날 자신의 자리로 돌아가지 못했습니다. 그래도 지금 둘은 사용자의 하루를 함께 지키며, 복잡한 마음을 천천히 풀고 있습니다."
  },
  {
    "memoryKey": "relation_zzori_mumu_easter_020",
    "characterTypeCode": "COMMON",
    "minLevel": 3,
    "fragmentType": "EASTER_EGG",
    "triggerType": "LOW_AFFECTION",
    "title": "같은 곁",
    "message": "무무가 비운 자리에 쪼리가 깃발을 꽂습니다.",
    "interpretation": "둘이 쓸쓸한 자리도 사라지지 않게 표시해두는 것 같아요.",
    "storyText": "쓸쓸한 날, 무무는 조용히 자리를 비웠고 쪼리는 그 옆에 작은 깃발을 꽂았습니다. 빈자리도 돌아올 수 있게 표시하면 길이 됩니다."
  }
]$story$::jsonb) AS raw(
        "memoryKey" TEXT,
        "characterTypeCode" TEXT,
        "minLevel" INT,
        "fragmentType" TEXT,
        "triggerType" TEXT,
        title TEXT,
        message TEXT,
        interpretation TEXT,
        "storyText" TEXT
    )
), normalized AS (
    SELECT
        "memoryKey" AS memory_key,
        REPLACE("characterTypeCode", 'ZZORI', 'JJORY') AS character_type_code,
        "minLevel" AS min_level,
        "fragmentType" AS fragment_type,
        "triggerType" AS trigger_type,
        title,
        CASE "memoryKey"
            WHEN 'zzori_lv3_lore_002' THEN '멈춘 자리까지도 내 지도에 있음.'
            WHEN 'zzori_lv3_easter_002' THEN '후퇴 아님. 안전 귀환임.'
            WHEN 'common_world_common_005' THEN '별친구들은 조용히 곁을 지킵니다.'
            WHEN 'common_world_lore_003' THEN '흐려진 이름도 다시 반짝일 수 있어요.'
            WHEN 'common_world_lore_010' THEN '별친구들은 심판 대신 보관을 택했습니다.'
            WHEN 'common_world_easter_008' THEN '아주 늦은 빛도 기록될 수 있어요.'
            ELSE message
        END AS message,
        CASE "memoryKey"
            WHEN 'mumu_lv2_common_005' THEN '무무가 밤의 문턱에서 오늘의 작은 기록을 조용히 덮어주는 것 같아요.'
            WHEN 'mumu_lv3_common_005' THEN '무무가 이제 밤의 판정을 두려워하기보다, 작은 하루를 직접 감싸는 것 같아요.'
            WHEN 'nova_lv2_common_005' THEN '노바가 늦게 도착한 빛도 보류하지 않고 바라보는 것 같아요.'
            WHEN 'nova_lv3_common_005' THEN '노바가 밤의 기준보다 오늘 남은 작은 빛을 먼저 보는 것 같아요.'
            WHEN 'zzori_lv2_easter_003' THEN '쪼리가 지도에 없는 길도 다시 찾을 수 있게 표시하는 것 같아요.'
            WHEN 'zzori_lv3_easter_003' THEN '쪼리가 돌아갈 수 없는 선이 아니라, 돌아올 수 있는 길을 남기는 것 같아요.'
            WHEN 'common_world_common_002' THEN 'Polaris가 작게 남은 잔광도 천천히 바라봐 주는 것 같아요.'
            WHEN 'common_world_common_003' THEN '증언 장부가 아주 짧은 움직임도 조용히 받아 적는 것 같아요.'
            WHEN 'common_world_common_004' THEN '밤하늘의 빈칸이 실패가 아니라 기다림의 자리처럼 느껴져요.'
            WHEN 'common_world_common_005' THEN '별친구들이 오늘을 검사하지 않고 곁에서 같이 확인해 주는 것 같아요.'
            WHEN 'common_world_lore_001' THEN '새벽법정의 오래된 기준이 오늘의 작은 실천을 조심스럽게 알아보는 것 같아요.'
            WHEN 'common_world_lore_002' THEN '별조각이 보상만이 아니라, 작은 하루가 남긴 증거처럼 느껴져요.'
            WHEN 'common_world_lore_003' THEN '이름 없는 그림자가 흐려 놓은 하루를 별친구들이 다시 밝혀 주는 것 같아요.'
            WHEN 'common_world_lore_004' THEN '완벽하지 않아도 기록된 하루는 사라지지 않는다는 신호 같아요.'
            WHEN 'common_world_lore_005' THEN '작은 루틴의 불씨가 조용히 오래 남을 수 있다고 말하는 것 같아요.'
            WHEN 'common_world_lore_006' THEN '흩어진 별친구들이 사용자의 작은 하루를 통해 서로를 다시 알아보는 것 같아요.'
            WHEN 'common_world_lore_007' THEN '씨앗과 별핵과 첫걸음이 서로 다른 상처를 안고 같은 방향을 보는 것 같아요.'
            WHEN 'common_world_lore_008' THEN '잊힌 이름표가 대단한 사건보다 평범한 숨을 더 오래 기억하는 것 같아요.'
            WHEN 'common_world_lore_009' THEN '오늘의 좌표가 먼 곳이 아니라 지금 있는 자리에서 시작되는 것 같아요.'
            WHEN 'common_world_lore_010' THEN '별친구들이 사용자를 심판하지 않고, 남은 빛을 함께 보관하기로 한 것 같아요.'
            WHEN 'common_world_lore_011' THEN '증언 장부에 새 줄이 생길 때마다 밤하늘의 빈칸이 조금 줄어드는 것 같아요.'
            WHEN 'common_world_lore_012' THEN '다시 켜진 자리가 예전과 다른 별자리로 자라나는 것 같아요.'
            WHEN 'common_world_easter_001' THEN '시스템 로그 뒤편에서 새벽법정이라는 낡은 이름이 잠깐 스치는 것 같아요.'
            WHEN 'common_world_easter_002' THEN '자정의 경계에서 오늘과 내일이 아주 잠깐 같은 빛을 나누는 것 같아요.'
            WHEN 'common_world_easter_003' THEN '세 별친구의 반응이 겹치며 오래된 별자리 선이 살짝 이어진 것 같아요.'
            WHEN 'common_world_easter_004' THEN '비어 있는 장부 칸이 비난이 아니라 다음 작은 실천을 기다리는 자리 같아요.'
            WHEN 'common_world_easter_005' THEN '늦게 도착한 별빛이 늦은 하루도 기록될 수 있다고 알려주는 것 같아요.'
            WHEN 'common_world_easter_006' THEN '하루 판정이 시작될 때 별친구들이 오늘의 기록을 한 번 더 감싸는 것 같아요.'
            WHEN 'common_world_easter_007' THEN '메시지 끝의 알 수 없는 별점이 오래된 별자리의 구두점처럼 느껴져요.'
            WHEN 'common_world_easter_008' THEN '아주 늦은 빛이 조용히 도착해도, Polaris는 그 빛을 놓치지 않는 것 같아요.'
            ELSE interpretation
        END AS interpretation,
        CASE "memoryKey"
            WHEN 'mumu_lv2_common_005' THEN '밤이 오면 새벽법정의 문턱은 여전히 희미하게 열려요. 하지만 무무의 잎맥은 전보다 선명해져, 오늘의 작은 일을 더 깊은 곳에 눌러 담습니다.'
            WHEN 'mumu_lv3_common_005' THEN '밤이 오면 새벽법정의 문턱이 열리지만, 무무는 더 이상 숨지만은 않아요. 잎 끝에 남은 작은 빛을 들어 올려, 이 하루도 사라지지 않는다고 조용히 증언합니다.'
            WHEN 'nova_lv2_common_005' THEN '밤의 새벽법정에서는 늦은 빛도 길이 됩니다. 노바는 예전처럼 보류하지 않고, 늦게 켜진 반짝임이 어디서 왔는지 한 번 더 살펴봅니다.'
            WHEN 'nova_lv3_common_005' THEN '밤의 새벽법정은 여전히 조용하지만, 노바의 시선은 더 부드러워졌어요. 기준에 닿지 못한 빛도 사용자의 곁에 남으면 별이 될 수 있다고 믿습니다.'
            WHEN 'zzori_lv2_easter_003' THEN '쪼리가 바닥에 보이지 않는 선을 다시 그었어요. 이번에는 지도에 없는 지형이 아니라, 멈췄던 자리에서 다시 출발할 수 있는 작은 표식처럼 남았습니다.'
            WHEN 'zzori_lv3_easter_003' THEN '쪼리가 바닥에 그은 보이지 않는 선은 금지선이 아니었어요. 사라질 뻔한 출발들을 되찾아 올 수 있게 남겨 둔, 아주 삐뚤빼뚤한 귀환 지도였습니다.'
            WHEN 'common_world_common_005' THEN '별친구가 곁에 있는 이유는 사용자의 하루를 채점하기 위해서가 아니에요. 남아 있는 작은 흔적을 같이 바라보고, 사라지지 않게 붙잡아 주기 위해서입니다.'
            WHEN 'common_world_lore_003' THEN '이름 없는 그림자는 커다란 괴담보다 조용한 흐림에 가까웠어요. 그래서 별친구들은 서두르지 않고, 남아 있는 기록부터 하나씩 밝히기로 했습니다.'
            WHEN 'common_world_lore_010' THEN '별친구들의 맹세는 사용자를 심판하지 않는 것입니다. 그들이 지키려는 건 완벽한 하루가 아니라, 사라지지 않았으면 하는 작은 빛입니다.'
            WHEN 'common_world_easter_008' THEN '가끔 아주 늦은 빛이 장부 가장자리에 내려앉습니다. 날짜가 조금 어긋나 보여도, Polaris는 그 빛이 도착했다는 사실을 먼저 기억합니다.'
            ELSE "storyText"
        END AS story_text,
        ROW_NUMBER() OVER (
            ORDER BY
                CASE REPLACE("characterTypeCode", 'ZZORI', 'JJORY')
                    WHEN 'MUMU' THEN 1
                    WHEN 'NOVA' THEN 2
                    WHEN 'JJORY' THEN 3
                    ELSE 4
                END,
                "minLevel",
                CASE "fragmentType"
                    WHEN 'COMMON' THEN 1
                    WHEN 'LORE' THEN 2
                    ELSE 3
                END,
                "memoryKey"
        ) AS sort_order
    FROM seed
)
INSERT INTO character_story_fragments (
    memory_key,
    character_type_code,
    min_level,
    fragment_type,
    trigger_type,
    title,
    message,
    interpretation,
    story_text,
    sort_order,
    active
)
SELECT
    memory_key,
    character_type_code,
    min_level,
    fragment_type,
    trigger_type,
    title,
    message,
    interpretation,
    story_text,
    sort_order,
    TRUE
FROM normalized;
