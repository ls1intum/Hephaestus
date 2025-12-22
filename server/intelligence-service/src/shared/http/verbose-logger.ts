import fs from "node:fs";
import path from "node:path";
import type { Context, MiddlewareHandler } from "hono";
import pino from "pino";

import env from "@/env";

/**
 * Verbose logging middleware that captures request/response bodies.
 *
 * Enable via: VERBOSE_LOGGING=true
 * Configure log file via: VERBOSE_LOG_FILE=logs/verbose.log
 *
 * This is an OPT-IN feature for debugging. It logs:
 * - Full request headers
 * - Request body (JSON parsed if possible)
 * - Response body (for non-streaming responses)
 * - Timing information
 *
 * WARNING: This can log sensitive data. Use only for debugging.
 */

let logStream: fs.WriteStream | null = null;

// Use pino for the startup message instead of console.log
const startupLogger = pino({ level: "info" });

function getLogStream(): fs.WriteStream {
	if (!logStream) {
		const logDir = path.dirname(env.VERBOSE_LOG_FILE);
		if (!fs.existsSync(logDir)) {
			fs.mkdirSync(logDir, { recursive: true });
		}
		logStream = fs.createWriteStream(env.VERBOSE_LOG_FILE, { flags: "a" });
	}
	return logStream;
}

function writeLog(entry: Record<string, unknown>) {
	const stream = getLogStream();
	const timestamp = new Date().toISOString();
	const line = JSON.stringify({ timestamp, ...entry }, null, 2);
	stream.write(`${line}\n${"─".repeat(80)}\n`);
}

function truncateBody(body: unknown, maxLength = 10000): unknown {
	const str = typeof body === "string" ? body : JSON.stringify(body);
	if (str && str.length > maxLength) {
		return `[TRUNCATED: ${str.length} chars] ${str.slice(0, maxLength)}...`;
	}
	return body;
}

async function captureJsonBody(c: Context): Promise<unknown> {
	const clone = c.req.raw.clone();
	const text = await clone.text();
	if (!text) {
		return null;
	}
	try {
		return JSON.parse(text);
	} catch {
		return text;
	}
}

async function captureTextBody(c: Context): Promise<string> {
	const clone = c.req.raw.clone();
	return await clone.text();
}

async function captureRequestBody(c: Context): Promise<unknown> {
	try {
		const contentType = c.req.header("content-type") || "";

		if (contentType.includes("application/json")) {
			return await captureJsonBody(c);
		}

		if (contentType.includes("text/")) {
			return await captureTextBody(c);
		}

		return `[Binary: ${contentType}]`;
	} catch (e) {
		return `[Error capturing body: ${e instanceof Error ? e.message : String(e)}]`;
	}
}

function getRequestHeaders(c: Context): Record<string, string> {
	const headers: Record<string, string> = {};
	c.req.raw.headers.forEach((value, key) => {
		// Redact sensitive headers
		if (["authorization", "cookie", "x-api-key"].includes(key.toLowerCase())) {
			headers[key] = "[REDACTED]";
		} else {
			headers[key] = value;
		}
	});
	return headers;
}

function logRequest(c: Context, requestId: string, requestBody: unknown) {
	const requestHeaders = getRequestHeaders(c);
	const requestEntry = {
		type: "REQUEST",
		requestId,
		method: c.req.method,
		url: c.req.url,
		path: c.req.path,
		query: c.req.query(),
		headers: requestHeaders,
		body: truncateBody(requestBody),
	};
	writeLog(requestEntry);
}

async function captureResponseBody(c: Context): Promise<unknown> {
	const contentType = c.res.headers.get("content-type") || "";

	if (contentType.includes("text/event-stream")) {
		return "[SSE Stream - not captured]";
	}

	if (!c.res.body) {
		return null;
	}

	try {
		const cloned = c.res.clone();
		const text = await cloned.text();
		if (!text) {
			return null;
		}
		try {
			return truncateBody(JSON.parse(text));
		} catch {
			return truncateBody(text);
		}
	} catch (e) {
		return `[Error capturing response: ${e instanceof Error ? e.message : String(e)}]`;
	}
}

async function logResponse(c: Context, requestId: string, duration: number) {
	const responseEntry: Record<string, unknown> = {
		type: "RESPONSE",
		requestId,
		method: c.req.method,
		path: c.req.path,
		status: c.res.status,
		duration: `${duration}ms`,
		responseHeaders: Object.fromEntries(c.res.headers.entries()),
		body: await captureResponseBody(c),
	};
	writeLog(responseEntry);
}

/**
 * Verbose logging middleware.
 * Only active when VERBOSE_LOGGING=true
 */
export function verboseLogger(): MiddlewareHandler {
	// Early return no-op middleware if not enabled
	if (!env.VERBOSE_LOGGING) {
		return async (_c, next) => {
			await next();
		};
	}

	startupLogger.info(`[VERBOSE LOGGING] Enabled - writing to ${env.VERBOSE_LOG_FILE}`);

	return async (c, next) => {
		const requestId = c.get("requestId") || crypto.randomUUID();
		const startTime = Date.now();

		// Capture and log request
		const requestBody = await captureRequestBody(c);
		logRequest(c, requestId, requestBody);

		// Execute the request
		await next();

		// Log response
		const duration = Date.now() - startTime;
		await logResponse(c, requestId, duration);
	};
}

/**
 * Close the log stream (for graceful shutdown)
 */
export function closeVerboseLogStream(): void {
	if (logStream) {
		logStream.end();
		logStream = null;
	}
}
