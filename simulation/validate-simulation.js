const fs = require('fs');
const path = require('path');

const simulationDir = __dirname;
const dataDir = path.join(simulationDir, 'test-datasets');
const requiredScripts = [
    'k6-simulation-script.js',
    'k6-ai-mock-smoke.js',
    'generate-synthetic-data.js',
    'ai-mock-server.js',
    'inject-chaos.ps1',
    'verify-and-cleanup.sql',
];
const requiredCsvHeaders = {
    'users.csv': ['user_id', 'provider', 'email', 'created_at', 'status'],
    'onboarding_profiles.csv': ['user_id', 'routine_goal', 'mission_intensity', 'completed'],
    'mission_events.csv': ['user_id', 'event_type', 'occurred_at'],
    'wallet_transactions.csv': ['user_id', 'idempotency_key', 'amount'],
    'share_events.csv': ['user_id', 'idempotency_key', 'share_type'],
    'ai_usage_logs.csv': ['user_id', 'request_id', 'latency_ms', 'status'],
};

for (const file of requiredScripts) {
    const target = path.join(simulationDir, file);
    if (!fs.existsSync(target) || fs.statSync(target).size === 0) {
        throw new Error(`Missing simulation artifact: ${file}`);
    }
}

for (const [file, headers] of Object.entries(requiredCsvHeaders)) {
    const target = path.join(dataDir, file);
    const lines = fs.readFileSync(target, 'utf8').trim().split(/\r?\n/);
    const actualHeaders = lines[0].split(',');
    for (const header of headers) {
        if (!actualHeaders.includes(header)) {
            throw new Error(`${file} is missing required column: ${header}`);
        }
    }
    if (lines.length < 2) {
        throw new Error(`${file} has no data rows`);
    }
    console.log(`${file}: ${lines.length - 1} rows`);
}

console.log('Simulation artifact validation passed.');
