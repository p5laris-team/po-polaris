-- =============================================================
-- Polaris Character Domain - Insert Initial Character Types
-- PRD 5.3 MVP 캐릭터 3종 (NOVA, MUMU, JJORY)
-- =============================================================

INSERT INTO character_types (code, name, summary, personality, speech_style, intro_message, sample_line, active, sort_order)
VALUES
(
    'NOVA',
    '노바',
    '자기가 별이었다는 걸 까먹은 별알',
    '다정함, 조심스러움, 기억이 듬성듬성함',
    '짧고 느림. 문장 끝이 작게 흐려짐',
    '노바는 자기가 한때 길을 비추던 별이었다는 걸 까먹은 별알이에요. 말은 조금 느리지만, 당신이 작은 일을 해낼 때마다 조금씩 빛을 되찾아요.',
    '오늘도… 있었네.',
    TRUE,
    1
),
(
    'MUMU',
    '무무',
    '“무…”밖에 못 하지만 다 알고 있는 작은 별나무',
    '말 없음, 기다림 많음, 은근 다정함',
    '거의 “무”만 말함. 시스템이 해석을 보조함',
    '무무는 말을 많이 하지 못해요. 하지만 당신이 작은 일을 해내면 잎이 조금 움직여요. 무무의 “무...”는 가끔 고맙다는 뜻일지도 몰라요.',
    '무…',
    TRUE,
    2
),
(
    'JJORY',
    '쪼리',
    '현관까지 가면 세계여행이라고 믿는 별쥐',
    '시크한 척함, 겁 많음, 허세 있지만 귀여움',
    '건조함, 짧은 농담, X 감성',
    '쪼리는 늘 가방을 메고 있지만 멀리 가진 못해요. 그래도 현관까지 가는 일을 아주 큰 모험이라고 믿어요. 작은 행동을 너무 크게 부풀리는 재능이 있어요.',
    '집 앞도 밖임. 반박 안 받음.',
    TRUE,
    3
);

-- 초기 테스트용 더미 캐릭터 에셋 이미지 삽입 (추후 CDN 연동 후 교체)
INSERT INTO character_assets (character_type_id, asset_type, asset_url)
SELECT id, 'IDLE', 'https://cdn.polaris.app/' || LOWER(code) || '/idle.png'
FROM character_types;

INSERT INTO character_assets (character_type_id, asset_type, asset_url)
SELECT id, 'SAD', 'https://cdn.polaris.app/' || LOWER(code) || '/sad.png'
FROM character_types;
