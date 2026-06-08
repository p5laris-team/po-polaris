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
 * - $ node simulation/ai-mock-server.js
 */

const express = require('express');
const app = express();

// JSON 형태의 요청 바디를 파싱하기 위한 미들웨어 설정
app.use(express.json());

// 전역 장애 상태를 저장하는 변수 (null: 정상, 'timeout', 'bad_request', 'broken_json')
let globalChaosType = null;

/**
 * [장애 주입 API - POST]
 * - 호출 예시: POST http://localhost:8085/chaos/inject (Body: { "type": "timeout" })
 * - type 종류: timeout, bad_request, broken_json, none
 */
app.post('/chaos/inject', (req, res) => {
    const type = req.query.type || req.body.type;
    if (['timeout', 'bad_request', 'broken_json', 'none'].includes(type)) {
        globalChaosType = type === 'none' ? null : type;
        console.log(`[Chaos Config] 전역 장애 설정 변경 -> ${globalChaosType || '장애 없음(정상)'}`);
        return res.json({ status: 'SUCCESS', message: `전역 장애가 '${globalChaosType || 'none'}'으로 설정되었습니다.` });
    }
    return res.status(400).json({ status: 'ERROR', message: "invalid chaos type. Use: timeout, bad_request, broken_json, none" });
});

/**
 * [장애 주입 API - GET]
 * - 호출 예시: GET http://localhost:8085/chaos/inject?type=timeout
 * - 브라우저나 PowerShell 등에서 편리하게 테스트 및 수동 주입을 위해 GET 방식도 지원합니다.
 */
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
 * [Gemini 2.5-flash generateContent 모킹 API]
 * - 백엔드 AI 모듈이 호출하는 외부 구글 AI API의 주소를 흉내 냅니다.
 * - 경로: POST /v1beta/models/gemini-2.5-flash:generateContent
 */
app.post('/v1beta/models/gemini-2.5-flash:generateContent', (req, res) => {
    // 1. 들어온 요청 프롬프트 데이터 간단 로깅 (테스트 중 동작 추적용)
    try {
        const contents = req.body.contents;
        if (contents && contents[0] && contents[0].parts && contents[0].parts[0]) {
            console.log(`[Gemini Request] 프롬프트 내용: ${contents[0].parts[0].text.substring(0, 80)}...`);
        }
    } catch (err) {
        console.log('[Gemini Request] 바디 구조가 규격과 다름:', req.body);
    }

    // 2. 장애(Chaos) 주입 우선순위 결정: 전역 설정(globalChaosType) 혹은 개별 요청 헤더('x-chaos-trigger')
    const chaosTrigger = globalChaosType || req.headers['x-chaos-trigger'];

    if (chaosTrigger) {
        console.log(`[Chaos Injection] 장애 트리거 적용 - 타입: ${chaosTrigger} (전역: ${!!globalChaosType})`);
        
        switch (chaosTrigger) {
            case 'timeout':
                // [장애 A] 5초 이상 응답을 강제 지연시킨 후 504 Gateway Timeout 반환하여 백엔드 타임아웃/서킷 브레이커 테스트 유발
                console.log('  -> [장애 주입] 5초 응답 지연 발생...');
                return setTimeout(() => {
                    res.status(504).send('Gateway Timeout - Fake Server Delayed Response');
                }, 5000);

            case 'bad_request':
                // [장애 B] 실제 Google API 공식 에러 형태의 400 Bad Request 객체를 반환하여 AI 연동 실패 시의 에러 핸들링 테스트
                console.log('  -> [장애 주입] 400 Bad Request 강제 응답...');
                return res.status(400).json({
                    error: {
                        code: 400,
                        message: "API key not valid. Please pass a valid API key.",
                        status: "INVALID_ARGUMENT"
                    }
                });

            case 'broken_json':
                // [장애 C] 닫히지 않은 깨진 JSON 문자열을 전송하여, 백엔드 LLM Parser의 JSON 파싱 실패 및 예외 처리를 테스트
                console.log('  -> [장애 주입] 깨진 JSON 결과물 반환...');
                res.setHeader('Content-Type', 'application/json');
                return res.status(200).send('{ "status": "SUCCESS", "message": "JSON이 닫히지 않았음... ');

            default:
                console.log('  -> [장애 주입] 알 수 없는 트리거이므로 정상 응답으로 전환합니다.');
        }
    }

    // 3. 정상 응답 시나리오 (실제 구글 Gemini API 응답 스키마와 동일하게 반환)
    // - candidates.content.parts[0].text에 JSON 포맷의 미션 피드백 결과 문자열을 바인딩합니다.
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

    // 지연 없이 정상적으로 즉각적인 200 OK 응답 반환
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
