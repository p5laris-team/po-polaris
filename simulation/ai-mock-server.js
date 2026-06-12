const express = require('express');

const CHAOS_TYPES = new Set(['timeout', 'bad_request', 'broken_json', 'none']);

function createApp({ timeoutMs = Number(process.env.MOCK_TIMEOUT_MS || 5000) } = {}) {
    const app = express();
    let chaosType = null;

    app.use(express.json());

    app.get('/health', (req, res) => {
        res.json({ status: 'UP', chaosType: chaosType || 'none' });
    });

    const configureChaos = (req, res) => {
        const requestedType = req.query.type || req.body?.type;
        if (!CHAOS_TYPES.has(requestedType)) {
            return res.status(400).json({
                status: 'ERROR',
                message: 'invalid chaos type. Use: timeout, bad_request, broken_json, none',
            });
        }

        chaosType = requestedType === 'none' ? null : requestedType;
        return res.json({ status: 'SUCCESS', chaosType: chaosType || 'none' });
    };

    app.get('/chaos/inject', configureChaos);
    app.post('/chaos/inject', configureChaos);

    app.post('/v1beta/models/gemini-2.5-flash:generateContent', (req, res) => {
        const requestChaosType = chaosType || req.header('x-chaos-trigger');

        if (requestChaosType === 'timeout') {
            return setTimeout(() => {
                res.status(504).send('Gateway Timeout - mock server delayed response');
            }, timeoutMs);
        }

        if (requestChaosType === 'bad_request') {
            return res.status(400).json({
                error: {
                    code: 400,
                    message: 'API key not valid. Please pass a valid API key.',
                    status: 'INVALID_ARGUMENT',
                },
            });
        }

        if (requestChaosType === 'broken_json') {
            res.type('application/json');
            return res.status(200).send('{ "status": "SUCCESS", "message": "truncated');
        }

        return res.json({
            candidates: [{
                content: {
                    parts: [{
                        text: JSON.stringify({
                            status: 'SUCCESS',
                            message: 'Mock AI guide response',
                        }),
                    }],
                    role: 'model',
                },
                finishReason: 'STOP',
                index: 0,
            }],
            usageMetadata: {
                promptTokenCount: 120,
                candidatesTokenCount: 45,
                totalTokenCount: 165,
            },
        });
    });

    return app;
}

function startServer({
    port = Number(process.env.AI_MOCK_PORT || 8085),
    timeoutMs = Number(process.env.MOCK_TIMEOUT_MS || 5000),
} = {}) {
    const server = createApp({ timeoutMs }).listen(port, () => {
        console.log(`AI mock server listening on http://localhost:${port}`);
    });
    return server;
}

if (require.main === module) {
    startServer();
}

module.exports = { createApp, startServer };
