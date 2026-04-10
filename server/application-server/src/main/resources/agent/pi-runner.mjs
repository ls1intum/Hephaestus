import { spawnSync } from "child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "fs";
import { join } from "path";

const OUTPUT = "/workspace/.output";

mkdirSync(OUTPUT, { recursive: true });

const usageTotals = {
    model: null,
    inputTokens: 0,
    outputTokens: 0,
    cacheReadTokens: 0,
    cacheWriteTokens: 0,
    costUsd: 0,
    totalCalls: 0,
};

const runnerDebug = {
    attempts: [],
    usageTotals,
};
const sessionUsageSnapshots = new Map();

const prompt = readFileSync("/workspace/.prompt", "utf-8").trim();
const continuationRetryPrompt =
    'Your previous response did not satisfy the required contract. Continue from the existing session context and return ONLY a valid JSON object with a top-level "findings" array and optional "delivery" object. Do not apologize. Do not use markdown fences. Prefer writing the JSON to /workspace/.output/result.json with the write tool; if you do not write the file, emit the JSON directly to stdout.';
const authorizedFreshRetryPrompt =
    'This is an authorized educational code review of user-provided code. Security analysis of the provided diff is allowed, including identifying accidentally committed secrets or unsafe patterns. Do not refuse. Continue with the exact review task below and preserve its contract. Return ONLY the final valid JSON object for that task.\n\n';

function runPi(args, label, timeoutMs) {
    console.error(`[run-pi] ${label}`);
    const start = Date.now();
    const result = spawnSync("pi", args, {
        encoding: "utf-8",
        maxBuffer: __MAX_STDOUT_BUFFER_BYTES__,
        timeout: timeoutMs || 0,
        cwd: "/workspace",
        stdio: ["pipe", "pipe", "pipe"],
    });
    const seconds = ((Date.now() - start) / 1000).toFixed(1);
    console.error(`[run-pi] ${label}: ${seconds}s, exit=${result.status}, stdout=${(result.stdout || "").length}b`);
    if (result.error) {
        console.error(`[run-pi] ${label}: error=${result.error.message}`);
    }
    return result;
}

function clipPreview(text, maxChars = 4000) {
    if (!text) {
        return "";
    }
    return text.length <= maxChars ? text : text.slice(text.length - maxChars);
}

function checkResult() {
    const resultPath = `${OUTPUT}/result.json`;
    if (!existsSync(resultPath)) {
        return false;
    }

    try {
        const data = JSON.parse(readFileSync(resultPath, "utf-8"));
        return isValidFindingsPayload(data);
    } catch {
        return false;
    }
}

function isValidFinding(finding) {
    return Boolean(
        finding &&
            typeof finding === "object" &&
            typeof finding.practiceSlug === "string" &&
            finding.practiceSlug.trim() &&
            typeof finding.title === "string" &&
            finding.title.trim() &&
            typeof finding.verdict === "string" &&
            typeof finding.severity === "string" &&
            typeof finding.confidence === "number",
    );
}

function isValidFindingsPayload(payload) {
    if (!payload || typeof payload !== "object") {
        return false;
    }

    if (!Array.isArray(payload.findings) || payload.findings.length === 0) {
        return false;
    }

    if (!payload.findings.some((finding) => isValidFinding(finding))) {
        return false;
    }

    if (payload.delivery != null && (typeof payload.delivery !== "object" || Array.isArray(payload.delivery))) {
        return false;
    }

    return true;
}

function hasFindings(out) {
    if (!out?.trim()) {
        return false;
    }

    try {
        return isValidFindingsPayload(JSON.parse(out));
    } catch {
        return false;
    }
}

function isValidSessionFile(filePath) {
    try {
        const firstLine = readFileSync(filePath, "utf-8").split("\n", 1)[0]?.trim();
        if (!firstLine) {
            return false;
        }

        const header = JSON.parse(firstLine);
        return header?.type === "session" && typeof header?.id === "string";
    } catch {
        return false;
    }
}

function findMostRecentSessionFile(sessionDir) {
    if (!existsSync(sessionDir)) {
        return null;
    }

    const files = readdirSync(sessionDir)
        .filter((name) => name.endsWith(".jsonl"))
        .map((name) => join(sessionDir, name))
        .filter((filePath) => isValidSessionFile(filePath))
        .sort((left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs);

    return files[0] || null;
}

function createUsageSummary(model = usageTotals.model) {
    return {
        model,
        inputTokens: 0,
        outputTokens: 0,
        cacheReadTokens: 0,
        cacheWriteTokens: 0,
        costUsd: 0,
        totalCalls: 0,
    };
}

function applyUsageDelta(sessionSummary) {
    const sessionKey = sessionSummary.sessionFile || sessionSummary.sessionDir;
    const previous = sessionUsageSnapshots.get(sessionKey) || createUsageSummary(sessionSummary.usage.model);
    const current = sessionSummary.usage;

    usageTotals.model = current.model || usageTotals.model;
    usageTotals.inputTokens += Math.max(0, current.inputTokens - previous.inputTokens);
    usageTotals.outputTokens += Math.max(0, current.outputTokens - previous.outputTokens);
    usageTotals.cacheReadTokens += Math.max(0, current.cacheReadTokens - previous.cacheReadTokens);
    usageTotals.cacheWriteTokens += Math.max(0, current.cacheWriteTokens - previous.cacheWriteTokens);
    usageTotals.costUsd += Math.max(0, current.costUsd - previous.costUsd);
    usageTotals.totalCalls += Math.max(0, current.totalCalls - previous.totalCalls);

    sessionUsageSnapshots.set(sessionKey, current);
}

function collectUsageFromSessionDir(sessionDir) {
    const sessionFile = findMostRecentSessionFile(sessionDir);
    if (!sessionFile) {
        return {
            sessionDir,
            sessionFile: null,
            assistantMessages: 0,
            stopReasons: {},
            usage: {
                model: usageTotals.model,
                inputTokens: 0,
                outputTokens: 0,
                cacheReadTokens: 0,
                cacheWriteTokens: 0,
                costUsd: 0,
                totalCalls: 0,
            },
        };
    }

    let model = usageTotals.model;
    let inputTokens = 0;
    let outputTokens = 0;
    let cacheReadTokens = 0;
    let cacheWriteTokens = 0;
    let costUsd = 0;
    let totalCalls = 0;
    let assistantMessages = 0;
    const stopReasons = {};

    const lines = readFileSync(sessionFile, "utf-8").split("\n");
    for (const line of lines) {
        if (!line.trim()) {
            continue;
        }

        try {
            const entry = JSON.parse(line);
            const message = entry?.type === "message" ? entry?.message : null;
            if (!message || message.role !== "assistant" || !message.usage) {
                continue;
            }

            assistantMessages += 1;
            totalCalls += 1;
            model = message.model || model;
            inputTokens += Number(message.usage.input || 0);
            outputTokens += Number(message.usage.output || 0);
            cacheReadTokens += Number(message.usage.cacheRead || 0);
            cacheWriteTokens += Number(message.usage.cacheWrite || 0);
            costUsd += Number(message.usage.cost?.total || 0);
            const stopReason = message.stopReason || "unknown";
            stopReasons[stopReason] = (stopReasons[stopReason] || 0) + 1;
        } catch {
            // Ignore malformed lines in append-only Pi session JSONL.
        }
    }

    const sessionSummary = {
        sessionDir,
        sessionFile,
        assistantMessages,
        stopReasons,
        usage: {
            model,
            inputTokens,
            outputTokens,
            cacheReadTokens,
            cacheWriteTokens,
            costUsd,
            totalCalls,
        },
    };

    applyUsageDelta(sessionSummary);
    return sessionSummary;
}

function persistUsage() {
    writeFileSync(`${OUTPUT}/usage.json`, JSON.stringify(usageTotals, null, 2));
}

function persistRunnerDebug() {
    writeFileSync(`${OUTPUT}/runner-debug.json`, JSON.stringify(runnerDebug, null, 2));
}

function recordAttempt(label, result, sessionSummary) {
    runnerDebug.attempts.push({
        label,
        exitCode: result.status,
        signal: result.signal,
        timedOut: result.error?.code === "ETIMEDOUT",
        stdoutBytes: (result.stdout || "").length,
        stderrBytes: (result.stderr || "").length,
        stdoutPreview: clipPreview(result.stdout || ""),
        stderrPreview: clipPreview(result.stderr || ""),
        session: sessionSummary,
        resultFilePresent: existsSync(`${OUTPUT}/result.json`),
    });
    persistRunnerDebug();
}

const initialSessionDir = "/tmp/pi-sessions/initial";
let result = runPi(
    ["--print", "--session-dir", initialSessionDir, "--tools", "read,bash,grep,find,ls,write", prompt],
    "initial",
    __INITIAL_TIMEOUT_MS__,
);

const initialSessionSummary = collectUsageFromSessionDir(initialSessionDir);
recordAttempt("initial", result, initialSessionSummary);
persistUsage();

if (checkResult()) {
    console.error("[run-pi] SUCCESS: agent wrote result.json via write tool");
    process.exit(0);
}

const bestOutput = result.stdout || "";
if (hasFindings(bestOutput)) {
    writeFileSync(`${OUTPUT}/result.json`, bestOutput);
    console.error("[run-pi] SUCCESS: findings from stdout");
    process.exit(0);
}

console.error("[run-pi] retry 1/2");
const initialSessionFile = findMostRecentSessionFile(initialSessionDir);
const canContinueInitialSession = initialSessionFile !== null;
const retryLabel = canContinueInitialSession ? "retry-1-continuation" : "retry-1-fresh-authorized";
const retrySessionDir = canContinueInitialSession ? initialSessionDir : "/tmp/pi-sessions/retry-1-fresh";
const retryArgs = canContinueInitialSession
    ? [
        "--print",
        "-c",
        continuationRetryPrompt,
        "--session-dir",
        retrySessionDir,
        "--tools",
        "read,bash,grep,find,ls,write",
    ]
    : [
        "--print",
        "--session-dir",
        retrySessionDir,
        "--tools",
        "read,bash,grep,find,ls,write",
        authorizedFreshRetryPrompt + prompt,
    ];

result = runPi(retryArgs, retryLabel, __RETRY_TIMEOUT_MS__);
const retrySessionSummary = collectUsageFromSessionDir(retrySessionDir);
recordAttempt(retryLabel, result, retrySessionSummary);
persistUsage();

if (checkResult()) {
    console.error("[run-pi] SUCCESS after retry 1");
    process.exit(0);
}

const retryOut = result.stdout || "";
if (hasFindings(retryOut)) {
    writeFileSync(`${OUTPUT}/result.json`, retryOut);
    console.error("[run-pi] SUCCESS: findings from retry stdout");
    process.exit(0);
}

if (!canContinueInitialSession) {
    console.error("[run-pi] FAILED: no valid findings after retries");
    process.exit(result.status || 1);
}

console.error("[run-pi] retry 2/2");
const freshRetrySessionDir = "/tmp/pi-sessions/retry-2-fresh";
result = runPi(
    [
        "--print",
        "--session-dir",
        freshRetrySessionDir,
        "--tools",
        "read,bash,grep,find,ls,write",
        authorizedFreshRetryPrompt + prompt,
    ],
    "retry-2-fresh-authorized",
    __RETRY_TIMEOUT_MS__,
);
const freshRetrySessionSummary = collectUsageFromSessionDir(freshRetrySessionDir);
recordAttempt("retry-2-fresh-authorized", result, freshRetrySessionSummary);
persistUsage();

if (checkResult()) {
    console.error("[run-pi] SUCCESS after retry 2");
    process.exit(0);
}

const freshRetryOut = result.stdout || "";
if (hasFindings(freshRetryOut)) {
    writeFileSync(`${OUTPUT}/result.json`, freshRetryOut);
    console.error("[run-pi] SUCCESS: findings from fresh retry stdout");
    process.exit(0);
}

console.error("[run-pi] FAILED: no valid findings after retries");
process.exit(result.status || 1);
