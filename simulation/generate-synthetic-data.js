const fs = require('fs');
const path = require('path');

const PERSONAS = [
    {
        name: 'EarlyBird_MorningLight',
        routineGoal: 'WAKE_UP',
        missionIntensity: 'LIGHT',
        preferredMissionTime: 'MORNING',
        activityPreference: 'INDOOR',
        livingType: 'LIVING_ALONE',
        wakeUpTime: '06:30',
        sleepTime: '22:30',
        completionProb: 0.85,
        shareProb: 0.30,
        ratio: 0.40
    },
    {
        name: 'NightOwl_HeavyOutdoor',
        routineGoal: 'EXERCISE',
        missionIntensity: 'HEAVY',
        preferredMissionTime: 'EVENING',
        activityPreference: 'OUTDOOR',
        livingType: 'LIVING_WITH_FAMILY',
        wakeUpTime: '09:00',
        sleepTime: '01:30',
        completionProb: 0.60,
        shareProb: 0.15,
        ratio: 0.30
    },
    {
        name: 'SelfDeveloper_MediumIndoor',
        routineGoal: 'STUDY',
        missionIntensity: 'MEDIUM',
        preferredMissionTime: 'AFTERNOON',
        activityPreference: 'INDOOR',
        livingType: 'LIVING_ALONE',
        wakeUpTime: '07:30',
        sleepTime: '23:30',
        completionProb: 0.75,
        shareProb: 0.50,
        ratio: 0.30
    }
];

function generateAllDatasets(userCount = 100) {
    console.log(`[시작] 6대 가상 데이터셋 및 SQL 사전 적재 스크립트 생성 중... (${userCount}명 기준)`);

    const users = [];
    const profiles = [];
    const missionEvents = [];
    const walletTransactions = [];
    const shareEvents = [];
    const aiUsageLogs = [];
    
    // 로컬 개발 DB 시퀀스와의 충돌을 방지하기 위해 가상 사용자 ID를 900001부터 부여
    let currentUserId = 900001;
    const dateStr = '2026-05-10';

    PERSONAS.forEach(persona => {
        const targetCount = Math.round(userCount * persona.ratio);
        
        for (let i = 0; i < targetCount; i++) {
            const userId = currentUserId++;
            const email = `simulation_test_${persona.name.toLowerCase()}_${userId}@polaris.com`;
            
            // 1. users
            users.push({
                user_id: userId,
                provider: 'GOOGLE',
                created_at: `${dateStr}T09:00:00`,
                status: 'ACTIVE',
                email: email
            });
            
            // 2. onboarding_profiles
            profiles.push({
                user_id: userId,
                living_type: persona.livingType,
                wake_up_time: persona.wakeUpTime,
                sleep_time: persona.sleepTime,
                preferred_mission_time: persona.preferredMissionTime,
                routine_goal: persona.routineGoal,
                mission_intensity: persona.missionIntensity,
                activity_preference: persona.activityPreference,
                completed: 'true'
            });

            // 3. mission_events & wallet_transactions
            const completedMission = Math.random() < persona.completionProb;
            const eventType = completedMission ? 'MISSION_COMPLETED' : 'MISSION_REJECTED';
            
            missionEvents.push({
                user_id: userId,
                mission_date: dateStr,
                stack_order: 1,
                category: 'HEALTH',
                difficulty: 'EASY',
                event_type: eventType,
                occurred_at: `${dateStr}T10:20:00`
            });

            if (completedMission) {
                walletTransactions.push({
                    user_id: userId,
                    transaction_type: 'EARN',
                    reason: 'MISSION_REWARD',
                    amount: 10,
                    balance_after: 110,
                    idempotency_key: `MISSION_REWARD:${userId}:${dateStr}`,
                    created_at: `${dateStr}T10:20:01`
                });
            }

            // 4. share_events
            if (Math.random() < persona.shareProb) {
                shareEvents.push({
                    user_id: userId,
                    share_date: dateStr,
                    share_type: 'COPY_LINK',
                    reward_paid: 'true',
                    reward_star_piece: 10,
                    idempotency_key: `SHARE_REWARD:${userId}:${dateStr}`,
                    created_at: `${dateStr}T10:25:00`
                });
            }

            // 5. ai_usage_logs
            aiUsageLogs.push({
                user_id: userId,
                request_id: `req-${userId}-001`,
                model: 'gemini-2.5-flash',
                latency_ms: Math.round(500 + Math.random() * 500),
                status: 'SUCCESS',
                error_type: 'null',
                created_at: `${dateStr}T09:01:00`
            });
        }
    });

    const dataDir = path.join(__dirname, 'test-datasets');
    if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir);
    }

    // CSV 파일들 저장
    fs.writeFileSync(path.join(dataDir, 'users.csv'), [
        'user_id,provider,email,created_at,status',
        ...users.map(u => `${u.user_id},${u.provider},${u.email},${u.created_at},${u.status}`)
    ].join('\n'));

    fs.writeFileSync(path.join(dataDir, 'onboarding_profiles.csv'), [
        'user_id,living_type,wake_up_time,sleep_time,preferred_mission_time,routine_goal,mission_intensity,activity_preference,completed',
        ...profiles.map(p => `${p.user_id},${p.living_type},${p.wake_up_time},${p.sleep_time},${p.preferred_mission_time},${p.routine_goal},${p.mission_intensity},${p.activity_preference},${p.completed}`)
    ].join('\n'));

    fs.writeFileSync(path.join(dataDir, 'mission_events.csv'), [
        'user_id,mission_date,stack_order,category,difficulty,event_type,occurred_at',
        ...missionEvents.map(m => `${m.user_id},${m.mission_date},${m.stack_order},${m.category},${m.difficulty},${m.event_type},${m.occurred_at}`)
    ].join('\n'));

    fs.writeFileSync(path.join(dataDir, 'wallet_transactions.csv'), [
        'user_id,transaction_type,reason,amount,balance_after,idempotency_key,created_at',
        ...walletTransactions.map(w => `${w.user_id},${w.transaction_type},${w.reason},${w.amount},${w.balance_after},${w.idempotency_key},${w.created_at}`)
    ].join('\n'));

    fs.writeFileSync(path.join(dataDir, 'share_events.csv'), [
        'user_id,share_date,share_type,reward_paid,reward_star_piece,idempotency_key,created_at',
        ...shareEvents.map(s => `${s.user_id},${s.share_date},${s.share_type},${s.reward_paid},${s.reward_star_piece},${s.idempotency_key},${s.created_at}`)
    ].join('\n'));

    fs.writeFileSync(path.join(dataDir, 'ai_usage_logs.csv'), [
        'user_id,request_id,model,latency_ms,status,error_type,created_at',
        ...aiUsageLogs.map(a => `${a.user_id},${a.request_id},${a.model},${a.latency_ms},${a.status},${a.error_type},${a.created_at}`)
    ].join('\n'));

    // DB 사전 적재용 SQL 쿼리 파일 자동 생성
    const sqlStatements = [];
    sqlStatements.push('-- Polaris 부하 시뮬레이션용 가상 데이터 사전 적재 SQL 스크립트');
    sqlStatements.push('BEGIN;');
    
    for (let idx = 0; idx < users.length; idx++) {
        const u = users[idx];
        const p = profiles[idx];
        
        // A. users 테이블 인서트
        sqlStatements.push(`INSERT INTO users (id, email, nickname, provider, role, status, created_at, updated_at) VALUES (${u.user_id}, '${u.email}', 'SimUser_${u.user_id}', 'GOOGLE', 'USER', 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING;`);
        
        // B. onboarding_profiles 테이블 인서트
        sqlStatements.push(`INSERT INTO onboarding_profiles (user_id, living_type, wake_up_time, sleep_time, preferred_mission_time, routine_goal, activity_preference, mission_intensity, completed, created_at, updated_at) VALUES (${u.user_id}, '${p.living_type}', '${p.wake_up_time}', '${p.sleep_time}', '${p.preferred_mission_time}', '${p.routine_goal}', '${p.activity_preference}', '${p.mission_intensity}', true, NOW(), NOW()) ON CONFLICT (user_id) DO NOTHING;`);
        
        // C. wallets 테이블 인서트 (가상 유저 기본 재화 500개 지급 세팅)
        sqlStatements.push(`INSERT INTO wallets (user_id, star_piece, created_at, updated_at) VALUES (${u.user_id}, 500, NOW(), NOW()) ON CONFLICT (user_id) DO NOTHING;`);
    }
    
    sqlStatements.push('COMMIT;');
    fs.writeFileSync(path.join(dataDir, 'insert-synthetic-data.sql'), sqlStatements.join('\n'));

    console.log(`[성공] 6대 가상 데이터셋 및 사전 적재 SQL 파일 출력 완료! (${dataDir}/ 하위)`);
}

generateAllDatasets(100);
