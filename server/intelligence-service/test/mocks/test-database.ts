/**
 * Test Database Utilities
 *
 * Utilities for integration testing against a real PostgreSQL database.
 * The database is provided by Testcontainers (see global-setup.ts).
 *
 * NO FALLBACKS - Tests require Docker. This is intentional:
 * - Consistent test environment across all machines
 * - CI/CD has Docker available
 * - Developers must have Docker installed (standard practice)
 */

import { eq, inArray, sql } from "drizzle-orm";
import db from "@/shared/db";
import { chatMessage, chatMessagePart, chatMessageVote, chatThread } from "@/shared/db/schema";

// ─────────────────────────────────────────────────────────────────────────────
// Test ID Generation
// ─────────────────────────────────────────────────────────────────────────────

let testIdCounter = 0;

/**
 * Generate a unique test ID to avoid collisions.
 */
export function testId(prefix = "test"): string {
	testIdCounter++;
	return `${prefix}-${Date.now()}-${testIdCounter}`;
}

/**
 * Generate a unique UUID for testing.
 */
export function testUuid(): string {
	return crypto.randomUUID();
}

// ─────────────────────────────────────────────────────────────────────────────
// Test Fixtures
// ─────────────────────────────────────────────────────────────────────────────

interface TestWorkspace {
	id: number;
	name: string;
}

interface TestUser {
	id: number;
	login: string;
	name: string | null;
}

interface TestGitProvider {
	id: number;
	type: string;
	serverUrl: string;
}

export interface TestFixtures {
	workspace: TestWorkspace;
	user: TestUser;
	gitProvider: TestGitProvider;
}

/**
 * Create minimal required test data (git_provider, workspace, user).
 * Uses raw SQL to bypass foreign key setup complexity.
 *
 * The git_provider is created first because user.provider_id (NOT NULL)
 * has a foreign key constraint referencing git_provider.id.
 *
 * Verifies inserts succeeded to avoid silent failures that can cause
 * FK constraint errors later in tests.
 */
export async function createTestFixtures(): Promise<TestFixtures> {
	// Use crypto.getRandomValues for better randomness (not security-critical, but good practice)
	const randomValue = crypto.getRandomValues(new Uint32Array(1))[0] ?? 0;
	const id = Date.now() + (randomValue % 1000);
	const workspaceId = id;
	const userId = id + 1;
	const gitProviderId = id + 2;
	const workspaceSlug = `test-ws-${id}`;
	const userLogin = `test-user-${id}`;
	const gitProviderServerUrl = `https://test-${id}.example.com`;

	// Insert git_provider - required FK for user.provider_id (NOT NULL)
	const gitProviderResult = await db.execute(sql`
		INSERT INTO git_provider (id, type, server_url, created_at)
		VALUES (${gitProviderId}, 'GITHUB', ${gitProviderServerUrl}, NOW())
		ON CONFLICT (id) DO NOTHING
		RETURNING id
	`);

	if (gitProviderResult.rowCount === 0) {
		const existingProvider = await db.execute(
			sql`SELECT id FROM git_provider WHERE id = ${gitProviderId}`,
		);
		if (existingProvider.rowCount === 0) {
			throw new Error(
				`Failed to create test git_provider: ID ${gitProviderId} conflicts but no row found. ` +
					`This may indicate a unique constraint violation on another column (e.g., type + server_url).`,
			);
		}
	}

	// Insert workspace - using actual schema columns
	// Use RETURNING to verify the insert succeeded
	const workspaceResult = await db.execute(sql`
		INSERT INTO workspace (id, created_at, updated_at, display_name, slug, account_type, status)
		VALUES (${workspaceId}, NOW(), NOW(), ${"Test Workspace"}, ${workspaceSlug}, 'USER', 'ACTIVE')
		ON CONFLICT (id) DO NOTHING
		RETURNING id
	`);

	// If ON CONFLICT triggered, the row wasn't inserted - verify it exists
	if (workspaceResult.rowCount === 0) {
		const existingWorkspace = await db.execute(
			sql`SELECT id FROM workspace WHERE id = ${workspaceId}`,
		);
		if (existingWorkspace.rowCount === 0) {
			throw new Error(
				`Failed to create test workspace: ID ${workspaceId} conflicts but no row found. ` +
					`This may indicate a unique constraint violation on another column (e.g., slug).`,
			);
		}
	}

	// Insert user - include all NOT NULL columns (native_id and provider_id are required)
	// Note: notifications_enabled and participate_in_research moved to user_preferences table
	const userResult = await db.execute(sql`
		INSERT INTO "user" (id, created_at, updated_at, login, name, type, native_id, provider_id)
		VALUES (${userId}, NOW(), NOW(), ${userLogin}, 'Test User', 'USER', ${userId}, ${gitProviderId})
		ON CONFLICT (id) DO NOTHING
		RETURNING id
	`);

	// If ON CONFLICT triggered, verify the user exists
	if (userResult.rowCount === 0) {
		const existingUser = await db.execute(sql`SELECT id FROM "user" WHERE id = ${userId}`);
		if (existingUser.rowCount === 0) {
			throw new Error(
				`Failed to create test user: ID ${userId} conflicts but no row found. ` +
					`This may indicate a unique constraint violation on another column.`,
			);
		}
	}

	return {
		workspace: { id: workspaceId, name: "Test Workspace" },
		user: { id: userId, login: userLogin, name: "Test User" },
		gitProvider: { id: gitProviderId, type: "GITHUB", serverUrl: gitProviderServerUrl },
	};
}

// ─────────────────────────────────────────────────────────────────────────────
// Cleanup Utilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clean up test threads and associated messages.
 */
export async function cleanupTestThread(threadId: string): Promise<void> {
	// First, clear selectedLeafMessageId to break circular FK reference
	await db.execute(sql`
		UPDATE chat_thread 
		SET selected_leaf_message_id = NULL 
		WHERE id = ${threadId}
	`);

	// Delete parts and votes first (FK constraints)
	const messages = await db
		.select({ id: chatMessage.id })
		.from(chatMessage)
		.where(eq(chatMessage.threadId, threadId));

	for (const msg of messages) {
		await db.delete(chatMessagePart).where(eq(chatMessagePart.messageId, msg.id));
		await db.delete(chatMessageVote).where(eq(chatMessageVote.messageId, msg.id));
	}

	// Delete messages
	await db.delete(chatMessage).where(eq(chatMessage.threadId, threadId));

	// Delete thread
	await db.delete(chatThread).where(eq(chatThread.id, threadId));
}

/**
 * Cleanup fixtures at the end of test.
 * Properly handles FK constraints by cleaning up in correct order.
 */
export async function cleanupTestFixtures(fixtures: TestFixtures): Promise<void> {
	// First, find all threads for this workspace
	const threads = await db
		.select({ id: chatThread.id })
		.from(chatThread)
		.where(eq(chatThread.workspaceId, fixtures.workspace.id));

	// Clean up each thread properly (handles FK constraints)
	for (const thread of threads) {
		await cleanupTestThread(thread.id);
	}

	// Now safe to delete workspace, user, and git_provider (in FK order)
	await db.execute(sql`DELETE FROM "user" WHERE id = ${fixtures.user.id}`);
	await db.execute(sql`DELETE FROM workspace WHERE id = ${fixtures.workspace.id}`);
	if (fixtures.gitProvider) {
		await db.execute(sql`DELETE FROM git_provider WHERE id = ${fixtures.gitProvider.id}`);
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Verification Utilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetch a thread with all its messages and parts for verification.
 */
export async function getTestThreadWithMessages(threadId: string) {
	const thread = await db.select().from(chatThread).where(eq(chatThread.id, threadId)).limit(1);

	if (thread.length === 0) {
		return null;
	}

	const messages = await db.select().from(chatMessage).where(eq(chatMessage.threadId, threadId));

	const messageIds = messages.map((m) => m.id);

	let parts: (typeof chatMessagePart.$inferSelect)[] = [];
	if (messageIds.length > 0) {
		parts = await db
			.select()
			.from(chatMessagePart)
			.where(inArray(chatMessagePart.messageId, messageIds));
	}

	return {
		thread: thread[0],
		messages,
		parts,
	};
}
