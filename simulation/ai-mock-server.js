/**
 * Polaris 부하 및 장애(Chaos) 시뮬레이션용 가짜 Gemini AI API 서버입니다.
 * 
 * [목적]
 * 1. 부하 테스트 시 실제 Gemini API 호출에 따른 막대한 비용 발생 및 Rate Limit 문제를 방지합니다.
 * 2. 런타임에 인위적으로 타임아웃, Bad Request, 깨진 JSON 형태의 장애를 백엔드에 주입하여
 *    백엔드 AI 모듈의 예외 복구(Fallback) 능력을 안전하게 시험합니다.
 * 
 * [사전 설치]
 * - 이 스크립트를 구동하기 전, 프로젝트 루트 폴더에서 아래 명령어로 express 패키지를 설치해야 합니다.
 *   $ npm install express
 * 
 * [실행 방법]
 * - $ node ai-mock-server.js
 */

const express = require('express');
const app = express();

// JSON 형태의 요청 바디를 파싱하기 위한 미들웨어 설정
app.use(express.json());

let globalChaosType = null;

// 장애(Chaos) 상태 주입 엔드포인트 추가
app.post('/chaos/inject', (req, res) => {
    const type = req.query.type || req.body.type;
    if (['timeout', 'bad_request', 'broken_json', 'none'].includes(type)) {
        globalChaosType = type === 'none' ? null : type;
        console.log(`[Chaos Config] 전역 장애 설정 변경 -> ${globalChaosType || '장애 없음(정상)'}`);
        return res.json({ status: 'SUCCESS', message: `전역 장애가 '${globalChaosType || 'none'}'으로 설정되었습니다.` });
    }
    return res.status(400).json({ status: 'ERROR', message: "invalid chaos type. Use: timeout, bad_request, broken_json, none" });
});

// GET 방식으로도 편리하게 제어 가능하도록 지원
app.get('/chaos/inject', (req, res) => {
    const type = req.query.type;
    if (['timeout', 'bad_request', 'broken_json', 'none'].includes(type)) {
        globalChaosType = type === 'none' ? null : type;
        console.log(`[Chaos Config] 전역 장애 설정 변경 (GET) -> ${globalChaosType || '장애 없음(정상)'}`);
        return res.json({ status: 'SUCCESS', message: `전역 장애가 '${globalChaosType || 'none'}'으로 설정되었습니다.` });
    }
    return res.status(400).json({ status: 'ERROR', message: "invalid chaos type. Use: timeout, bad_request, broken_json, none" });
});

/**
 * Gemini 2.5-flash 모델의 콘텐츠 생성 API 요청을 대리하여 모킹(Mocking) 처리합니다.
 * 백엔드 ai 모듈의 엔드포인트를 이 API 주소로 변경하여 테스트합니다.
 */
app.post('/v1beta/models/gemini-2.5-flash:generateContent', (req, res) => {
    // 1. 들어온 요청 프롬프트 데이터 간단 로깅 (모니터링용)
    try {
        const contents = req.body.contents;
        if (contents && contents[0] && contents[0].parts && contents[0].parts[0]) {
            console.log(`[Gemini Request] 프롬프트 내용: ${contents[0].parts[0].text.substring(0, 80)}...`);
        }
    } catch (err) {
        console.log('[Gemini Request] 바디 구조가 규격과 다름:', req.body);
    }

    // 2. 장애(Chaos) 주입 검증: 전역 설정 또는 요청 헤더
    const chaosTrigger = globalChaosType || req.headers['x-chaos-trigger'];

    if (chaosTrigger) {
        console.log(`[Chaos Injection] 장애 트리거 적용 - 타입: ${chaosTrigger} (전역: ${!!globalChaosType})`);
        
        switch (chaosTrigger) {
            case 'timeout':
                // 시나리오 A: 5초 이상 응답을 인위적으로 지연시킨 후 504 Gateway Timeout 반환 (타임아웃 / Fallback 테스트)
                console.log('  -> [장애 주입] 5초 응답 지연 발생...');
                return setTimeout(() => {
                    res.status(504).send('Gateway Timeout - Fake Server Delayed Response');
                }, 5000);

            case 'bad_request':
                // 시나리오 B: Google API 공식 에러 형태의 400 Bad Request 반환 (AI Provider 실패 테스트)
                console.log('  -> [장애 주입] 400 Bad Request 강제 응답...');
                return res.status(400).json({
                    error: {
                        code: 400,
                        message: "API key not valid. Please pass a valid API key.",
                        status: "INVALID_ARGUMENT"
                    }
                });

            case 'broken_json':
                // 시나리오 C: 파싱 불가능한 망가진 JSON 형태의 원시 텍스트 반환 (Invalid Output 파싱 에러 테스트)
                console.log('  -> [장애 주입] 깨진 JSON 결과물 반환...');
                res.setHeader('Content-Type', 'application/json');
                return res.status(200).send('{ "status": "SUCCESS", "message": "JSON이 닫히지 않았음... ');

            default:
                console.log('  -> [장애 주입] 알 수 없는 트리거이므로 정상 응답으로 전환합니다.');
        }
    }

    // 3. 정상 응답 시나리오 (프로젝트 AI 응답 포맷인 JSON 형태의 String을 후보 콘텐츠로 실어 보냄)
    // 아래 텍스트는 실제 Polaris AI 미션 피드백/가이드가 JSON 형식으로 에스케이프되어 적재되는 것을 묘사한 것입니다.
    const mockAiResponse = {
        candidates: [{
            content: {
                parts: [{
                    text: JSON.stringify({
                        status: "SUCCESS",
                        message: "가상 AI 가이드: 오늘의 루틴 미션 실천을 완료했습니다. 훌륭한 아침 습관입니다!"
                    }, null, 2)
                }],
                role: "model"
            },
            finishReason: "STOP",
            index: 0
        }],
        usageMetadata: {
            promptTokenCount: 120,
            candidatesTokenCount: 45,
            totalTokenCount: 165
        }
    };

    // 지연 없는 정상 즉각 응답
    res.json(mockAiResponse);
});

// AI Mock 서버 포트 8085로 로컬 구동 시작
const PORT = 8085;
app.listen(PORT, () => {
    console.log('====================================================');
    console.log(` Fake Gemini AI API Mock Server가 가동되었습니다.`);
    console.log(` - 주소: http://localhost:${PORT}`);
    console.log(` - 테스트 엔드포인트: POST http://localhost:${PORT}/v1beta/models/gemini-2.5-flash:generateContent`);
    console.log('====================================================');
});
