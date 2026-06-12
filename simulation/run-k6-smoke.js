const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const simulationDir = __dirname;
const resultsDir = path.join(simulationDir, 'results');
const dockerCommand = process.platform === 'win32' ? 'docker.exe' : 'docker';
const image = process.env.K6_IMAGE || 'grafana/k6:0.54.0';
const port = Number(process.env.AI_MOCK_PORT || 8085);

function waitForHealth(url, timeoutMs = 10000) {
    const startedAt = Date.now();
    return new Promise((resolve, reject) => {
        const poll = async () => {
            try {
                const response = await fetch(url);
                if (response.ok) {
                    resolve();
                    return;
                }
            } catch (error) {
                // The mock process may still be starting.
            }

            if (Date.now() - startedAt >= timeoutMs) {
                reject(new Error(`AI mock server did not become healthy: ${url}`));
                return;
            }
            setTimeout(poll, 100);
        };
        poll();
    });
}

async function main() {
    fs.mkdirSync(resultsDir, { recursive: true });
    const mock = spawn(process.execPath, [path.join(simulationDir, 'ai-mock-server.js')], {
        env: {
            ...process.env,
            AI_MOCK_PORT: String(port),
            MOCK_TIMEOUT_MS: process.env.MOCK_TIMEOUT_MS || '250',
        },
        stdio: 'inherit',
    });

    try {
        await waitForHealth(`http://localhost:${port}/health`);
        const volume = `${simulationDir}:/scripts`;
        const result = spawnSync(dockerCommand, [
            'run',
            '--rm',
            '--add-host=host.docker.internal:host-gateway',
            '-e',
            `AI_MOCK_URL=http://host.docker.internal:${port}`,
            '-v',
            volume,
            image,
            'run',
            '--summary-export=/scripts/results/k6-ai-mock-smoke-summary.json',
            '/scripts/k6-ai-mock-smoke.js',
        ], { stdio: 'inherit' });

        if (result.error) {
            throw result.error;
        }
        if (result.status !== 0) {
            throw new Error(`k6 smoke test failed with exit code ${result.status}`);
        }
    } finally {
        mock.kill();
    }
}

main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
});
