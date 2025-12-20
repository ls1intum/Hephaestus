#!/usr/bin/env npx tsx
/**
 * Sync local prompts to Langfuse.
 *
 * This script exports prompt definitions from the repository to Langfuse,
 * enabling collaborative editing and A/B testing while keeping git as source of truth.
 *
 * Usage:
 *   npx tsx scripts/sync-prompts.ts              # Sync all prompts
 *   npx tsx scripts/sync-prompts.ts --dry-run    # Preview without creating
 *   npx tsx scripts/sync-prompts.ts --export     # Export to JSON file
 *
 * Environment:
 *   LANGFUSE_PUBLIC_KEY  - Langfuse public key
 *   LANGFUSE_SECRET_KEY  - Langfuse secret key
 *   LANGFUSE_BASE_URL    - Langfuse base URL (optional)
 */

import fs from "node:fs";
import path from "node:path";
import { LangfuseClient } from "@langfuse/client";

// Import all prompt definitions
import { badPracticeDetectorPrompt } from "../src/prompts/detector/bad-practice.prompt";
import { mentorChatPrompt } from "../src/prompts/mentor/chat.prompt";
import type { PromptDefinition } from "../src/prompts/types";

// ─────────────────────────────────────────────────────────────────────────────
// Configuration
// ─────────────────────────────────────────────────────────────────────────────

/** All prompts to sync */
const PROMPTS: PromptDefinition[] = [badPracticeDetectorPrompt, mentorChatPrompt];

// ─────────────────────────────────────────────────────────────────────────────
// Export Format
// ─────────────────────────────────────────────────────────────────────────────

interface PromptExport {
	version: "1.0";
	exportedAt: string;
	prompts: Array<{
		name: string;
		type: "text" | "chat";
		prompt: string | Array<{ role: string; content: string }>;
		description?: string;
		config?: Record<string, unknown>;
		labels?: string[];
		tags?: string[];
		variables?: string[];
	}>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────────────────────────────────────

async function main() {
	const args = process.argv.slice(2);
	const isDryRun = args.includes("--dry-run");
	const isExport = args.includes("--export");

	console.log(`\n📋 Found ${PROMPTS.length} prompt(s) to sync\n`);

	// ─────────────────────────────────────────────────────────────────────────
	// Export Mode
	// ─────────────────────────────────────────────────────────────────────────
	if (isExport) {
		const exportData: PromptExport = {
			version: "1.0",
			exportedAt: new Date().toISOString(),
			prompts: PROMPTS.map((p) => ({
				name: p.name,
				type: p.type,
				prompt: p.prompt,
				description: p.description,
				config: p.config,
				labels: p.labels,
				tags: p.tags,
				variables: p.variables,
			})),
		};

		const outPath = path.join(import.meta.dirname, "../prompts-export.json");
		fs.writeFileSync(outPath, JSON.stringify(exportData, null, 2));
		console.log(`✅ Exported to ${outPath}\n`);
		return;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Dry Run Mode
	// ─────────────────────────────────────────────────────────────────────────
	if (isDryRun) {
		for (const prompt of PROMPTS) {
			console.log(`[DRY RUN] Would sync: ${prompt.name}`);
			console.log(`  Type: ${prompt.type}`);
			console.log(`  Labels: ${prompt.labels?.join(", ") || "none"}`);
			console.log(`  Tags: ${prompt.tags?.join(", ") || "none"}`);
			console.log(`  Variables: ${prompt.variables?.join(", ") || "none"}`);
			console.log(`  Description: ${prompt.description?.slice(0, 60) || "none"}...`);
			console.log("");
		}
		return;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Sync Mode
	// ─────────────────────────────────────────────────────────────────────────
	if (!(process.env.LANGFUSE_PUBLIC_KEY && process.env.LANGFUSE_SECRET_KEY)) {
		console.error("❌ Missing LANGFUSE_PUBLIC_KEY or LANGFUSE_SECRET_KEY");
		process.exit(1);
	}

	const langfuse = new LangfuseClient();
	let created = 0;
	let updated = 0;
	let unchanged = 0;
	let failed = 0;

	for (const prompt of PROMPTS) {
		try {
			// Only handle text prompts for now
			if (prompt.type !== "text") {
				console.log(`⏭️  ${prompt.name} - skipped (chat prompts not yet supported)`);
				continue;
			}

			// Check if prompt exists - use type: "text" for proper type inference
			let existingPromptContent: string | null = null;
			try {
				const existing = await langfuse.prompt.get(prompt.name, {
					label: "production",
					type: "text",
				});
				existingPromptContent = existing.prompt;
			} catch {
				// Prompt doesn't exist
			}

			if (existingPromptContent !== null) {
				// Check if content changed
				const newContent = prompt.prompt;

				if (existingPromptContent === newContent) {
					console.log(`⏭️  ${prompt.name} - unchanged`);
					unchanged++;
					continue;
				}

				// Create new version (text prompt)
				await langfuse.prompt.create({
					name: prompt.name,
					type: "text",
					prompt: prompt.prompt as string,
					config: prompt.config,
					labels: prompt.labels,
					tags: prompt.tags,
				});
				console.log(`🔄 ${prompt.name} - updated (new version)`);
				updated++;
			} else {
				// Create new prompt (text prompt)
				await langfuse.prompt.create({
					name: prompt.name,
					type: "text",
					prompt: prompt.prompt as string,
					config: prompt.config,
					labels: prompt.labels,
					tags: prompt.tags,
				});
				console.log(`✅ ${prompt.name} - created`);
				created++;
			}
		} catch (error) {
			console.error(`❌ ${prompt.name} - failed:`, error);
			failed++;
		}
	}

	// Note: LangfuseClient v4 doesn't have flushAsync/shutdownAsync
	// Prompts are created synchronously via API

	console.log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
	console.log(`✅ Sync complete: ${created} created, ${updated} updated, ${unchanged} unchanged`);
	if (failed > 0) {
		console.log(`❌ ${failed} failed`);
		process.exit(1);
	}
}

main().catch((error) => {
	console.error("Fatal error:", error);
	process.exit(1);
});
