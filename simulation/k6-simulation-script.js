/**
 * Polaris 마이크로서비스 통합 부하 및 동시성 정합성 검증을 위한 k6 시나리오 스크립트입니다.
 * 
 * [동작 흐름]
 * 1. 6대 가상 데이터셋 CSV 파일(users, profiles, mission_events, transactions, share_events, ai_logs)을 로드합니다.
 * 2. 게이트웨이 테스트 토큰 API(/api/auth/v1/test/token)를 호출하여 JWT 액세스 토큰을 바이패스로 획득합니다.
 * 3. 획득한 토큰과 가상 데이터셋 값을 기반으로 온보딩, 캐릭터 생성, AI 대화(장애주입 포함), 미션, 공유, 아이템 구매 연타 시나리오를 구동합니다.
 * 
 * [실행 방식]
 * - $ docker-compose -f docker-compose-kafka.yml run --rm k6
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. 6대 가상 데이터셋 CSV 로드 및 파싱
const users = new SharedArray('users', function () {
    return papaparse.parse(open('./test-datasets/users.csv'), { header: true }).data;
});

const profiles = new SharedArray('profiles', function () {
    return papaparse.parse(open('./test-datasets/onboarding_profiles.csv'), { header: true }).data;
});

const missionEvents = new SharedArray('mission_events', function () {
    return papaparse.parse(open('./test-datasets/mission_events.csv'), { header: true }).data;
});

const transactions = new SharedArray('wallet_transactions', function () {
    return papaparse.parse(open('./test-datasets/wallet_transactions.csv'), { header: true }).data;
});

const shareEvents = new SharedArray('share_events', function () {
    return papaparse.parse(open('./test-datasets/share_events.csv'), { header: true }).data;
});

const aiLogs = new SharedArray('ai_logs', function () {
    return papaparse.parse(open('./test-datasets/ai_usage_logs.csv'), { header: true }).data;
});

// 2. 부하 시나리오 구성 (VU Ramping 설정)
// 로컬 환경의 자원 고갈 및 커넥션 타임아웃을 예방하기 위해 최대 동시 사용자를 10명으로 조율하고, 점진적으로 ramping 합니다.
export const options = {
    scenarios: {
        polaris_stress_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 5 },  // 15초 동안 5명으로 서서히 증가
                { duration: '30s', target: 10 }, // 30초 동안 10명 유지 (로컬 안정적인 부하선)
                { duration: '10s', target: 0 },  // 10초 동안 서서히 기동 중지
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'], 
        http_req_duration: ['p(95)<500'], 
    },
};

// 3. 부하 테스트 실행 (가상 유저별 루프)
export default function () {
    // 가상 유저(VU) 번호에 따라 순차적으로 가상 사용자 데이터 할당
    const index = (__VU - 1) % users.length;
    const user = users[index];
    const profile = profiles[index];
    const missionEvent = missionEvents[index % missionEvents.length];
    const transaction = transactions[index % transactions.length];
    const shareEvent = shareEvents[index % shareEvents.length];
    const aiLog = aiLogs[index % aiLogs.length];

    // 로컬 실행 시 기본값은 localhost:8080, Docker Compose 실행 시 주입된 환경변수 적용
    const gatewayUrl = __ENV.GATEWAY_URL || 'http://localhost:8080';

    // --- 시나리오 A: [가입/인증 우회] 테스트 토큰 발급 API 호출 ---
    const tokenRes = http.get(`${gatewayUrl}/api/auth/v1/test/token?userId=${user.user_id}`);
    
    const isTokenOk = check(tokenRes, {
        '1-1. 테스트 토큰 발급 성공 (200)': (r) => r.status === 200,
        '1-2. JWT 엑세스 토큰 존재 확인': (r) => r.json().data.accessToken !== undefined
    });

    if (!isTokenOk) {
        console.error(`[에러] 토큰 발급 실패 - 유저 ID: ${user.user_id}, 응답코드: ${tokenRes.status}`);
        sleep(1);
        return;
    }

    const token = tokenRes.json().data.accessToken;
    const authHeaders = {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    };
    
    // 백엔드 연결 부하를 분산하기 위해 각 연쇄 요청 사이에 적절한 Think Time(sleep) 도입
    sleep(0.5);

    // --- 시나리오 B: 온보딩 설정 저장 (Save Profile) ---
    // 실제 API: PUT /api/onboarding/v1/profiles/me
    const onboardingPayload = JSON.stringify({
        livingType: profile.living_type,
        wakeUpTime: profile.wake_up_time,
        sleepTime: profile.sleep_time,
        preferredMissionTime: profile.preferred_mission_time,
        routineGoal: profile.routine_goal,
        missionIntensity: profile.mission_intensity,
        activityPreference: profile.activity_preference,
        completed: true
    });

    const onboardRes = http.put(`${gatewayUrl}/api/onboarding/v1/profiles/me`, onboardingPayload, { headers: authHeaders });
    check(onboardRes, {
        '2. 온보딩 프로필 저장 성공': (r) => r.status === 200
    });
    
    sleep(0.5);

    // --- 시나리오 C: 캐릭터 조회 및 생성 ---
    let characterId = null;
    const getCharRes = http.get(`${gatewayUrl}/api/character/v1/characters/me`, { headers: authHeaders });
    
    if (getCharRes.status === 200 && getCharRes.json().data && getCharRes.json().data.id) {
        characterId = getCharRes.json().data.id;
    } else {
        const createCharPayload = JSON.stringify({
            characterTypeId: 1, // 기본 캐릭터 타입 1
            name: `Friend_${user.user_id}`
        });
        const createCharRes = http.post(`${gatewayUrl}/api/character/v1/characters`, createCharPayload, { headers: authHeaders });
        const isCharCreated = check(createCharRes, {
            '3. 캐릭터 생성 성공': (r) => r.status === 200 || r.status === 201
        });
        if (isCharCreated && createCharRes.json().data) {
            characterId = createCharRes.json().data.id;
        }
    }

    sleep(0.5);

    if (characterId) {
        // --- 시나리오 D: AI 대화 SSE 스트리밍 (Gemini Mock 연동) ---
        // 실제 API: POST /api/character/v1/characters/{characterId}/talk/stream
        const talkPayload = JSON.stringify({
            message: `오늘 ${missionEvent.category} 미션을 어떻게 깨는 게 좋을까? 추천해줘!`,
            interactionType: "TALK"
        });
        
        // 부하 도중 일부 요청에 인위적 Chaos 헤더 주입하여 Fallback 성능 검사 (10% 확률)
        const chatHeaders = Object.assign({}, authHeaders);
        if (Math.random() < 0.10) {
            chatHeaders['x-chaos-trigger'] = 'timeout'; // 타임아웃 유발
        }

        const talkRes = http.post(`${gatewayUrl}/api/character/v1/characters/${characterId}/talk/stream`, talkPayload, { headers: chatHeaders });
        check(talkRes, {
            '4. AI 대화 스트리밍 응답 완료': (r) => r.status === 200 || r.status === 504 // timeout chaos 주입 시 504 허용
        });

        sleep(0.5);

        // --- 시나리오 E: 미션 플로우 (조회 -> 생성 -> 세션 -> 완료) ---
        let currentMissionId = null;
        
        // 1) 현재 미션 조회
        const currMissionRes = http.get(`${gatewayUrl}/api/mission/v1/missions/current`, { headers: authHeaders });
        if (currMissionRes.status === 200 && currMissionRes.json().data) {
            currentMissionId = currMissionRes.json().data.id;
        }

        // 2) 현재 미션이 없다면 다음 미션 제안받기
        if (!currentMissionId) {
            const nextMissionPayload = JSON.stringify({
                characterId: characterId,
                lastMissionId: 0
            });
            const nextMissionRes = http.post(`${gatewayUrl}/api/mission/v1/missions/today-focus/next`, nextMissionPayload, { headers: authHeaders });
            if (nextMissionRes.status === 200 && nextMissionRes.json().data) {
                currentMissionId = nextMissionRes.json().data.id;
            }
        }

        sleep(0.5);

        // 3) 미션 완료 처리 (세션 시작 및 답변 제출)
        if (currentMissionId) {
            // 완료 세션 시작
            const sessionRes = http.post(`${gatewayUrl}/api/mission/v1/missions/${currentMissionId}/completion-sessions`, {}, { headers: authHeaders });
            
            if (sessionRes.status === 200) {
                // 답변 제출
                const answerPayload = JSON.stringify({
                    answer: `오늘 ${missionEvent.category || 'HEALTH'} 관련 루틴 미션을 무사히 수행하여 성공적으로 마쳤습니다!`
                });
                const answerRes = http.post(`${gatewayUrl}/api/mission/v1/missions/${currentMissionId}/completion-answers`, answerPayload, { headers: authHeaders });
                check(answerRes, {
                    '5. 미션 완료 처리 성공': (r) => r.status === 200 || r.status === 409
                });
            }
        }

        sleep(0.5);

        // --- 시나리오 F: 공유 카드 생성 및 보상 수령 (Saga 멱등성 검증) ---
        // 1) 공유 카드 생성
        const shareCardPayload = JSON.stringify({
            characterId: characterId,
            headline: `오늘도 별친구와 함께 ${missionEvent.category || 'BASIC'} 루틴 완료!`,
            imageUrl: "https://polaris.cdn/shares/card_01.png"
        });
        const shareCardRes = http.post(`${gatewayUrl}/api/share/v1/share-cards`, shareCardPayload, { headers: authHeaders });
        
        if (shareCardRes.status === 200 && shareCardRes.json().data) {
            const shareCardId = shareCardRes.json().data.shareCardId;
            
            sleep(0.2); // 동시성 연타 직전 아주 짧은 대기

            // 2) 공유 보상 신청 연타 (멱등키 검증을 위해 동일 멱등키로 0.01초 간격 2번 요청)
            const shareIdempotencyKey = `SHARE_REWARD:${user.user_id}:${shareEvent.share_date || '2026-05-10'}`;
            const shareEventPayload = JSON.stringify({
                shareCardId: shareCardId,
                platform: "KAKAOTALK",
                shareType: shareEvent.share_type || "COPY_LINK",
                idempotencyKey: shareIdempotencyKey
            });

            const s1 = http.post(`${gatewayUrl}/api/share/v1/share-events`, shareEventPayload, { headers: authHeaders });
            const s2 = http.post(`${gatewayUrl}/api/share/v1/share-events`, shareEventPayload, { headers: authHeaders });

            check(s1, { '6-1. 첫 공유 보상 수령': (r) => r.status === 200 || r.status === 202 });
            check(s2, { '6-2. 공유 중복 보상 제어 확인': (r) => r.status === 200 || r.status === 409 });
        }
    }

    sleep(0.5);

    // --- 시나리오 G: 상점 조회 및 아이템 중복 구매 연타 (Saga 멱등성 검증) ---
    // 1) 상점 아이템 목록 조회
    http.get(`${gatewayUrl}/api/item/v1/items`, { headers: authHeaders });

    sleep(0.2); // 연타 직전 짧은 대기

    // 2) 아이템 구매 중복 요청 (동일 멱등키로 연타)
    const purchaseIdempotencyKey = `ITEM_PURCHASE:${user.user_id}:${transaction.idempotency_key || '1002'}`;
    const purchasePayload = JSON.stringify({
        itemId: 1, // 상점 기본 아이템 ID 1 가정
        quantity: 1,
        idempotencyKey: purchaseIdempotencyKey
    });

    const p1 = http.post(`${gatewayUrl}/api/item/v1/item-purchases`, purchasePayload, { headers: authHeaders });
    const p2 = http.post(`${gatewayUrl}/api/item/v1/item-purchases`, purchasePayload, { headers: authHeaders });

    check(p1, { '7-1. 첫 구매 요청 완료': (r) => r.status === 200 || r.status === 202 });
    check(p2, { '7-2. 중복 구매 멱등 차단 확인': (r) => r.status === 200 || r.status === 409 });

    sleep(1);
}
