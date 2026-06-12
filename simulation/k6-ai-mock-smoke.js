import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.AI_MOCK_URL || 'http://host.docker.internal:8085';
const generateUrl = `${baseUrl}/v1beta/models/gemini-2.5-flash:generateContent`;
const payload = JSON.stringify({
    contents: [{ parts: [{ text: 'Recommend a short routine mission.' }] }],
});
const headers = { 'Content-Type': 'application/json' };
const expectedStatuses = {
    responseCallback: http.expectedStatuses(200, 400, 504),
};

export const options = {
    scenarios: {
        ai_mock_smoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        http_req_duration: ['p(95)<1000'],
    },
};

function setChaos(type) {
    const response = http.get(`${baseUrl}/chaos/inject?type=${type}`);
    check(response, {
        [`chaos '${type}' configured`]: (res) => res.status === 200 && res.json('chaosType') === type,
    });
}

function invokeAi(expectedStatus, label) {
    const response = http.post(generateUrl, payload, {
        headers,
        ...expectedStatuses,
    });
    check(response, {
        [label]: (res) => res.status === expectedStatus,
    });
    return response;
}

export default function () {
    const health = http.get(`${baseUrl}/health`);
    check(health, {
        'mock server is healthy': (res) => res.status === 200 && res.json('status') === 'UP',
    });

    const normal = invokeAi(200, 'normal AI response returns 200');
    check(normal, {
        'normal AI response follows Gemini shape': (res) => Boolean(res.json('candidates.0.content.parts.0.text')),
    });

    setChaos('bad_request');
    invokeAi(400, 'bad request chaos returns 400');

    setChaos('broken_json');
    const broken = invokeAi(200, 'broken JSON chaos returns 200');
    check(broken, {
        'broken JSON cannot be parsed': (res) => {
            try {
                res.json();
                return false;
            } catch (error) {
                return true;
            }
        },
    });

    setChaos('timeout');
    invokeAi(504, 'timeout chaos returns 504');

    setChaos('none');
    invokeAi(200, 'AI response recovers after chaos reset');
}
