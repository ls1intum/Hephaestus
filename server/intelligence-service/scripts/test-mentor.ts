#!/usr/bin/env tsx
/**
 * Mentor Testing Script - Easy to use, rich output
 *
 * USAGE:
 *   npm run test:mentor -- "Your message"                    # New conversation
 *   npm run test:mentor -- --thread <id> "Follow-up"         # Continue thread
 *   npm run test:mentor -- --show-db <threadId>              # Show thread from DB
 *
 * EXAMPLES:
 *   npm run test:mentor -- "hey"
 *   npm run test:mentor -- "What have I been working on?"
 *   npm run test:mentor -- "Help me reflect on my week"
 *   npm run test:mentor -- --thread abc123 "thanks, that's helpful"
 *   npm run test:mentor -- --show-db abc123
 *
 * OUTPUT INTERPRETATION:
 *   🧵 Thread ID    - Use this for follow-up messages
 *   👤 User         - Your message
 *   🤖 Heph         - Assistant response (streamed)
 *   📦 Tool         - Tools called by the model
 *   📄 Document     - If a document was created/updated
 *   💾 DB State     - Final database state (messages, document)
 */

import { randomUUID } from "node:crypto";
import pg from "pg";

const BASE_URL = process.env.MENTOR_URL || "http://localhost:8000";
const DB_URL = process.env.DATABASE_URL || "postgresql://root:root@localhost:5432/hephaestus";

// Test user configuration
const TEST_USER = {
	id: "5898705",
	login: "FelixTJDietrich",
	name: "Felix Dietrich",
	workspaceId: "2",
	workspaceSlug: "ls1intum",
};

const HEADERS = {
	"Content-Type": "application/json",
	"x-user-id": TEST_USER.id,
	"x-user-login": TEST_USER.login,
	"x-user-name": TEST_USER.name,
	"x-workspace-id": TEST_USER.workspaceId,
	"x-workspace-slug": TEST_USER.workspaceSlug,
};

// ─────────────────────────────────────────────────────────────────────────────
// Database queries
// ─────────────────────────────────────────────────────────────────────────────

async function getThreadFromDB(threadId: string): Promise<{
	thread: Record<string, unknown> | null;
	messages: Record<string, unknown>[];
	document: Record<string, unknown> | null;
}> {
	const client = new pg.Client(DB_URL);
	await client.connect();

	try {
		// Get thread (actual table name: chat_thread)
		const threadResult = await client.query(
			"SELECT id, title, workspace_id, created_at FROM chat_thread WHERE id = $1",
			[threadId],
		);
		const thread = threadResult.rows[0] || null;

		// Get messages (actual table name: chat_message)
		const messagesResult = await client.query(
			`SELECT m.id, m.role, m.created_at,
			        array_agg(json_build_object('type', p.type, 'text', p.content) ORDER BY p.order_index) as parts
			 FROM chat_message m
			 LEFT JOIN chat_message_part p ON p.message_id = m.id
			 WHERE m.thread_id = $1 
			 GROUP BY m.id, m.role, m.created_at
			 ORDER BY m.created_at ASC`,
			[threadId],
		);
		const messages = messagesResult.rows;

		// Get document - documents are linked by user_id + workspace_id, order by created_at
		// For now, just get the most recent document for this user
		let document: Record<string, unknown> | null = null;
		try {
			const docResult = await client.query(
				`SELECT id, title, kind, content, created_at
				 FROM document
				 WHERE user_id = (SELECT user_id FROM chat_thread WHERE id = $1)
				   AND workspace_id = (SELECT workspace_id FROM chat_thread WHERE id = $1)
				 ORDER BY created_at DESC
				 LIMIT 1`,
				[threadId],
			);
			document = docResult.rows[0] || null;
		} catch {
			// Document table may not exist or have different schema
		}

		return { thread, messages, document };
	} finally {
		await client.end();
	}
}

function formatMessage(msg: Record<string, unknown>): string {
	const role = msg.role === "user" ? "👤" : "🤖";
	// Parts come from the aggregated JSON array - each part's 'text' field contains JSON with actual text
	const parts = msg.parts as Array<{ type: string; text?: string }> | null;
	const textParts: string[] = [];

	if (parts) {
		for (const part of parts) {
			// The 'text' field from SQL is actually the content JSON
			if (part.text && part.type === "text") {
				try {
					const parsed = typeof part.text === "string" ? JSON.parse(part.text) : part.text;
					if (parsed?.text) {
						textParts.push(parsed.text);
					}
				} catch {
					// If parsing fails, use as-is
					if (typeof part.text === "string") {
						textParts.push(part.text);
					}
				}
			}
		}
	}

	const text = textParts.join("\n").slice(0, 300);
	return `${role} ${msg.role}: ${text}${text.length >= 300 ? "..." : ""}`;
}

async function showDBState(threadId: string): Promise<void> {
	console.log(`\n${"═".repeat(80)}`);
	console.log("💾 DATABASE STATE");
	console.log(`${"═".repeat(80)}`);

	const { thread, messages, document } = await getThreadFromDB(threadId);

	if (!thread) {
		console.log(`No thread found with ID: ${threadId}`);
		return;
	}

	console.log(`\n🧵 Thread: ${thread.id}`);
	console.log(`   Title: ${thread.title || "(no title)"}`);
	console.log(`   Created: ${thread.created_at}`);

	console.log(`\n📨 Messages (${messages.length}):`);
	for (const msg of messages) {
		console.log(`   ${formatMessage(msg)}`);
	}

	if (document) {
		console.log(`\n📄 Document:`);
		console.log(`   Title: ${document.title}`);
		console.log(`   Kind: ${document.kind}`);
		console.log(`   Updated: ${document.updated_at}`);
		console.log(`   Content:`);
		console.log(`${"─".repeat(40)}`);
		const content = (document.content as string) || "";
		console.log(content.slice(0, 2000));
		if (content.length > 2000) {
			console.log("... (truncated)");
		}
		console.log(`${"─".repeat(40)}`);
	} else {
		console.log("\n📄 Document: (none created yet)");
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Streaming chat
// ─────────────────────────────────────────────────────────────────────────────

async function sendMessage(message: string, threadId: string): Promise<void> {
	const body = {
		id: threadId,
		message: {
			id: randomUUID(),
			role: "user",
			parts: [{ type: "text", text: message }],
		},
	};

	console.log(`\n${"═".repeat(80)}`);
	console.log(`🧵 Thread: ${threadId}`);
	console.log(`👤 User: ${message}`);
	console.log(`${"═".repeat(80)}\n`);

	const response = await fetch(`${BASE_URL}/mentor/chat`, {
		method: "POST",
		headers: HEADERS,
		body: JSON.stringify(body),
	});

	if (!response.ok) {
		console.error(`❌ HTTP Error: ${response.status} ${response.statusText}`);
		const text = await response.text();
		console.error(text);
		process.exit(1);
	}

	const reader = response.body?.getReader();
	if (!reader) {
		console.error("No response body");
		process.exit(1);
	}

	const decoder = new TextDecoder();
	const toolCalls: Array<{ name: string; input: unknown; output: unknown }> = [];
	let currentToolName = "";
	let currentToolInput: unknown = null;
	let fullResponse = "";
	let documentCreated = false;

	console.log("🤖 Heph:");
	console.log("─".repeat(40));

	while (true) {
		const { done, value } = await reader.read();
		if (done) {
			break;
		}

		const chunk = decoder.decode(value, { stream: true });
		const lines = chunk.split("\n");

		for (const line of lines) {
			if (!line.startsWith("data:")) {
				continue;
			}
			const data = line.slice(5).trim();
			if (!data || data === "[DONE]") {
				continue;
			}

			try {
				const parsed = JSON.parse(data);

				// Handle text-delta events (AI SDK format)
				if (parsed.type === "text-delta" && parsed.delta) {
					process.stdout.write(parsed.delta);
					fullResponse += parsed.delta;
				} else if (parsed.type === "text" && parsed.text) {
					// Also handle direct text events
					process.stdout.write(parsed.text);
					fullResponse += parsed.text;
				} else if (parsed.type === "tool-call") {
					currentToolName = parsed.toolName || "";
					currentToolInput = parsed.args;
					console.log(`\n\n📦 Tool: ${currentToolName}`);
					if (currentToolInput) {
						console.log(`   Input: ${JSON.stringify(currentToolInput)}`);
					}
				} else if (parsed.type === "tool-result") {
					const output = parsed.result;
					toolCalls.push({ name: currentToolName, input: currentToolInput, output });

					// Show relevant output summaries
					if (output?.summary) {
						const sum = output.summary;
						console.log(
							`   📊 This week: ${sum.mergedPRsThisWeek || 0} merged, ${sum.openPRs || 0} open`,
						);
					}
					if (output?.insights?.length > 0) {
						console.log(`   💡 ${output.insights[0]}`);
					}
					if (output?.pullRequests?.length > 0) {
						console.log(`   📋 ${output.pullRequests.length} PRs fetched`);
					}
				} else if (parsed.type === "document-delta" || parsed.type === "document-created") {
					documentCreated = true;
				}
			} catch {
				// Ignore parse errors
			}
		}
	}

	console.log(`\n${"─".repeat(40)}`);

	// Summary
	console.log(`\n📊 SUMMARY`);
	console.log(`   Response length: ${fullResponse.length} chars`);
	console.log(`   Tools called: ${toolCalls.map((t) => t.name).join(", ") || "none"}`);
	if (documentCreated) {
		console.log(`   📄 Document was created/updated`);
	}

	// Show DB state
	await showDBState(threadId);

	// Next steps
	console.log(`\n${"═".repeat(80)}`);
	console.log("💡 NEXT STEPS:");
	console.log(`   Continue: npm run test:mentor -- --thread ${threadId} "Your follow-up"`);
	console.log(`   View DB:  npm run test:mentor -- --show-db ${threadId}`);
	console.log(`${"═".repeat(80)}\n`);
}

// ─────────────────────────────────────────────────────────────────────────────
// CLI
// ─────────────────────────────────────────────────────────────────────────────

interface ParsedArgs {
	mode: "chat" | "show-db";
	message: string;
	threadId: string;
}

function parseArgs(): ParsedArgs {
	const args = process.argv.slice(2);
	let threadId: string = randomUUID();
	let message = "";
	let mode: "chat" | "show-db" = "chat";

	for (let i = 0; i < args.length; i++) {
		const arg = args[i];
		if (arg === "--thread" && args[i + 1]) {
			threadId = args[i + 1] ?? randomUUID();
			i++;
		} else if (arg === "--show-db" && args[i + 1]) {
			mode = "show-db";
			threadId = args[i + 1] ?? "";
			i++;
		} else if (arg && !arg.startsWith("--")) {
			message = arg;
		}
	}

	if (mode === "chat" && !message) {
		console.log(`
╔══════════════════════════════════════════════════════════════════════════════╗
║                        MENTOR TESTING SCRIPT                                  ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ USAGE:                                                                        ║
║   npm run test:mentor -- "message"              New conversation              ║
║   npm run test:mentor -- --thread <id> "msg"    Continue thread               ║
║   npm run test:mentor -- --show-db <id>         View thread from DB           ║
║                                                                               ║
║ EXAMPLE MESSAGES:                                                             ║
║   "hey"                                         Test greeting                 ║
║   "What have I been working on?"                Test activity fetch           ║
║   "Help me reflect on my week"                  Test reflection flow          ║
║   "I'm feeling stuck on this migration"         Test empathy                  ║
║   "thanks, that's all"                          Test closing                  ║
║                                                                               ║
║ TEST USER: ${TEST_USER.name} (${TEST_USER.login})                               ║
╚══════════════════════════════════════════════════════════════════════════════╝
`);
		process.exit(1);
	}

	return { mode, message, threadId };
}

async function main() {
	const { mode, message, threadId } = parseArgs();

	if (mode === "show-db") {
		await showDBState(threadId);
	} else {
		await sendMessage(message, threadId);
	}
}

main().catch(console.error);
