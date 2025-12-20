#!/usr/bin/env npx tsx
/**
 * Import prompts from Langfuse.
 *
 * This script fetches prompt definitions from Langfuse and outputs them
 * in a format that can be used to update local prompt definitions.
 *
 * Usage:
 *   npx tsx scripts/import-prompts.ts              # Import all prompts
 *   npx tsx scripts/import-prompts.ts --json       # Output as JSON
 *   npx tsx scripts/import-prompts.ts --diff       # Show diff with local
 *
 * Environment:
 *   LANGFUSE_PUBLIC_KEY  - Langfuse public key
 *   LANGFUSE_SECRET_KEY  - Langfuse secret key
 *   LANGFUSE_BASE_URL    - Langfuse base URL
 */

import { LangfuseClient } from "@langfuse/client";

// Import all prompt definitions (colocated with their features)
import { badPracticeDetectorPrompt } from "../src/detector/bad-practice.prompt";
import { mentorChatPrompt } from "../src/mentor/chat.prompt";
import type { PromptDefinition } from "../src/prompts/types";

// ─────────────────────────────────────────────────────────────────────────────
// Configuration
// ─────────────────────────────────────────────────────────────────────────────

/** All prompts to import */
const PROMPTS: PromptDefinition[] = [badPracticeDetectorPrompt, mentorChatPrompt];

// ─────────────────────────────────────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────────────────────────────────────

async function main() {
	const args = process.argv.slice(2);
	const isJson = args.includes("--json");
	const isDiff = args.includes("--diff");

	if (!(process.env.LANGFUSE_PUBLIC_KEY && process.env.LANGFUSE_SECRET_KEY)) {
		console.error("❌ Missing LANGFUSE_PUBLIC_KEY or LANGFUSE_SECRET_KEY");
		process.exit(1);
	}

	const langfuse = new LangfuseClient();
	const results: Array<{
		name: string;
		localVersion: string;
		langfuseVersion: string;
		langfusePromptVersion: number;
		isDifferent: boolean;
	}> = [];

	for (const prompt of PROMPTS) {
		// Only handle text prompts for now
		if (prompt.type !== "text") {
			continue;
		}

		try {
			const langfusePrompt = await langfuse.prompt.get(prompt.name, {
				label: "production",
				type: "text",
			});

			const localContent = prompt.prompt as string;
			const langfuseContent = langfusePrompt.prompt;
			const isDifferent = localContent !== langfuseContent;

			results.push({
				name: prompt.name,
				localVersion: `${localContent.slice(0, 100)}...`,
				langfuseVersion: `${langfuseContent.slice(0, 100)}...`,
				langfusePromptVersion: langfusePrompt.version,
				isDifferent,
			});

			if (isDiff && isDifferent) {
				console.log(`\n━━━ ${prompt.name} ━━━`);
				console.log(`Langfuse version: ${langfusePrompt.version}`);
				console.log("\n--- LOCAL ---");
				console.log(`${localContent.slice(0, 500)}...`);
				console.log("\n--- LANGFUSE ---");
				console.log(`${langfuseContent.slice(0, 500)}...`);
				console.log("\n⚠️  DIFFERENT - update local definition if Langfuse is source of truth");
			}
		} catch (error) {
			console.error(`❌ ${prompt.name}: Failed to fetch from Langfuse`, error);
		}
	}

	if (isJson) {
		console.log(JSON.stringify(results, null, 2));
	} else if (!isDiff) {
		console.log("\n📋 Prompt Comparison:\n");
		for (const result of results) {
			const status = result.isDifferent ? "⚠️  DIFFERENT" : "✅ SAME";
			console.log(`${status} ${result.name} (Langfuse v${result.langfusePromptVersion})`);
		}
	}
}

main().catch((error) => {
	console.error("Fatal error:", error);
	process.exit(1);
});
