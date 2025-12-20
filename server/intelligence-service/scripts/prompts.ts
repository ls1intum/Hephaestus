#!/usr/bin/env npx tsx
/**
 * Prompt Management CLI
 *
 * Automatic bidirectional sync between local prompt files and Langfuse.
 *
 * ZERO CONFIGURATION REQUIRED:
 * - Prompts are auto-discovered from src/prompts/**\/*.prompt.ts
 * - Tool definitions are auto-discovered from *.tool.ts files in _meta.toolsDir
 * - Just edit your files and run the CLI
 *
 * Uses ts-morph for safe AST-based code manipulation.
 *
 * @example
 *   npm run prompts           # Check sync status (default)
 *   npm run prompts push      # Push local → Langfuse
 *   npm run prompts pull      # Pull Langfuse → local
 *   npm run prompts list      # List discovered prompts
 */

import "dotenv/config";
import path from "node:path";
import { LangfuseClient } from "@langfuse/client";
import { Command } from "commander";
import { glob } from "glob";
import ora from "ora";
import pc from "picocolors";

import type { PromptDefinition, PromptToolDefinition } from "../src/prompts/types";
import {
	updateParameterDescriptionInFile,
	updatePromptInFile,
	updateToolDescriptionInFile,
} from "./ast-utils";

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const SRC_DIR = path.join(import.meta.dirname, "../src");

// ─────────────────────────────────────────────────────────────────────────────
// Auto-Discovery
// ─────────────────────────────────────────────────────────────────────────────

interface DiscoveredTool {
	name: string;
	filePath: string;
	relativePath: string;
}

interface DiscoveredPrompt {
	/** Absolute path to the prompt file */
	filePath: string;
	/** Path relative to src/ */
	relativePath: string;
	/** The exported prompt definition */
	definition: PromptDefinition;
	/** Discovered tool files in the toolsDir */
	tools: DiscoveredTool[];
}

/**
 * Find tool files in a directory and map tool names to file paths
 */
async function discoverToolFiles(toolsDir: string): Promise<DiscoveredTool[]> {
	const pattern = path.join(toolsDir, "*.tool.ts");
	const files = await glob(pattern, { absolute: true });
	const tools: DiscoveredTool[] = [];

	for (const filePath of files) {
		try {
			const module = await import(filePath);

			// Find exports ending in "Definition"
			for (const exportName of Object.keys(module)) {
				if (exportName.endsWith("Definition")) {
					const def = module[exportName];
					if (def?.function?.name) {
						tools.push({
							name: def.function.name,
							filePath,
							relativePath: path.relative(SRC_DIR, filePath),
						});
					}
				}
			}
		} catch {
			// Skip files that fail to import
		}
	}

	return tools;
}

/**
 * Auto-discover all prompt files matching *.prompt.ts in src/
 * Prompts are colocated with their features (e.g., mentor/chat.prompt.ts)
 * Multiple prompts can be exported from a single file.
 */
async function discoverPrompts(): Promise<DiscoveredPrompt[]> {
	const pattern = path.join(SRC_DIR, "**/*.prompt.ts");
	const files = await glob(pattern, { absolute: true });

	const prompts: DiscoveredPrompt[] = [];
	const seenNames = new Set<string>(); // Track unique prompt names

	for (const filePath of files) {
		try {
			const module = await import(filePath);

			// Find ALL exported PromptDefinitions in the file
			for (const exportName of Object.keys(module)) {
				const exported = module[exportName];
				if (isPromptDefinition(exported)) {
					// Skip if we've already seen this prompt name (handles default + named export)
					if (seenNames.has(exported.name)) {
						continue;
					}
					seenNames.add(exported.name);

					const relativePath = path.relative(SRC_DIR, filePath);

					// Discover tools in the toolsDir (only for prompts that have it)
					let tools: DiscoveredTool[] = [];
					if (exported._meta?.toolsDir) {
						const toolsDirPath = path.join(SRC_DIR, exported._meta.toolsDir);
						tools = await discoverToolFiles(toolsDirPath);
					}

					prompts.push({
						filePath,
						relativePath,
						definition: exported,
						tools,
					});
					// Don't break - allow multiple prompts per file
				}
			}
		} catch (err) {
			console.error(`Failed to load ${filePath}:`, err);
		}
	}

	return prompts;
}

function isPromptDefinition(obj: unknown): obj is PromptDefinition {
	return (
		typeof obj === "object" &&
		obj !== null &&
		"name" in obj &&
		"type" in obj &&
		"prompt" in obj &&
		typeof (obj as PromptDefinition).name === "string" &&
		((obj as PromptDefinition).type === "text" || (obj as PromptDefinition).type === "chat")
	);
}

// ─────────────────────────────────────────────────────────────────────────────
// Output Helpers
// ─────────────────────────────────────────────────────────────────────────────

const symbols = {
	success: pc.green("✓"),
	error: pc.red("✗"),
	warning: pc.yellow("!"),
	info: pc.blue("ℹ"),
	arrow: pc.dim("→"),
	bullet: pc.dim("•"),
};

function header(text: string): void {
	console.log();
	console.log(pc.bold(pc.cyan(text)));
	console.log(pc.dim("─".repeat(60)));
}

function success(text: string): void {
	console.log(`  ${symbols.success} ${text}`);
}

function error(text: string): void {
	console.log(`  ${symbols.error} ${pc.red(text)}`);
}

function warning(text: string): void {
	console.log(`  ${symbols.warning} ${pc.yellow(text)}`);
}

function info(text: string): void {
	console.log(`  ${symbols.info} ${text}`);
}

function detail(text: string): void {
	console.log(`    ${symbols.bullet} ${pc.dim(text)}`);
}

function summary(stats: { updated: number; unchanged: number; failed: number }): void {
	console.log();
	console.log(pc.dim("─".repeat(60)));
	const parts: string[] = [];
	if (stats.updated > 0) {
		parts.push(pc.blue(`${stats.updated} updated`));
	}
	if (stats.unchanged > 0) {
		parts.push(pc.dim(`${stats.unchanged} unchanged`));
	}
	if (stats.failed > 0) {
		parts.push(pc.red(`${stats.failed} failed`));
	}
	console.log(`  ${parts.join(pc.dim(" • "))}`);
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

function ensureCredentials(): LangfuseClient {
	if (!(process.env.LANGFUSE_PUBLIC_KEY && process.env.LANGFUSE_SECRET_KEY)) {
		console.log();
		error("Missing Langfuse credentials");
		detail("Set LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY in .env");
		console.log();
		process.exit(1);
	}
	return new LangfuseClient();
}

function deepSortKeys(obj: unknown): unknown {
	if (obj === null || typeof obj !== "object") {
		return obj;
	}
	if (Array.isArray(obj)) {
		return obj.map(deepSortKeys);
	}
	const sorted: Record<string, unknown> = {};
	for (const key of Object.keys(obj as Record<string, unknown>).sort()) {
		sorted[key] = deepSortKeys((obj as Record<string, unknown>)[key]);
	}
	return sorted;
}

function configsEqual(
	a: Record<string, unknown> | undefined,
	b: Record<string, unknown> | undefined,
): boolean {
	return JSON.stringify(deepSortKeys(a || {})) === JSON.stringify(deepSortKeys(b || {}));
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Comparison Types and Functions
// ─────────────────────────────────────────────────────────────────────────────

interface ParameterSchema {
	type?: string;
	description?: string;
	enum?: string[];
	minimum?: number;
	maximum?: number;
	[key: string]: unknown;
}

interface ToolParameters {
	type?: string;
	properties?: Record<string, ParameterSchema>;
	required?: string[];
	[key: string]: unknown;
}

interface ParameterDiff {
	paramName: string;
	field: string;
	local: unknown;
	langfuse: unknown;
}

interface ToolDiff {
	name: string;
	descriptionDiff?: { local: string; langfuse: string };
	parameterDiffs: ParameterDiff[];
	missingInLocal: string[];
	missingInLangfuse: string[];
}

/**
 * Deep comparison of two tools, including parameters
 */
function compareTools(
	local: PromptToolDefinition | undefined,
	langfuse: PromptToolDefinition,
): ToolDiff | null {
	const toolName = langfuse.function.name;

	if (!local) {
		return {
			name: toolName,
			descriptionDiff: { local: "(not found)", langfuse: langfuse.function.description || "" },
			parameterDiffs: [],
			missingInLocal: [],
			missingInLangfuse: [],
		};
	}

	const diff: ToolDiff = {
		name: toolName,
		parameterDiffs: [],
		missingInLocal: [],
		missingInLangfuse: [],
	};

	// Compare descriptions
	if (local.function.description !== langfuse.function.description) {
		diff.descriptionDiff = {
			local: local.function.description || "",
			langfuse: langfuse.function.description || "",
		};
	}

	// Compare parameters
	const localParams = (local.function.parameters as ToolParameters)?.properties || {};
	const lfParams = (langfuse.function.parameters as ToolParameters)?.properties || {};

	const allParamNames = new Set([...Object.keys(localParams), ...Object.keys(lfParams)]);

	for (const paramName of allParamNames) {
		const localParam = localParams[paramName];
		const lfParam = lfParams[paramName];

		if (!localParam) {
			diff.missingInLocal.push(paramName);
			continue;
		}
		if (!lfParam) {
			diff.missingInLangfuse.push(paramName);
			continue;
		}

		// Compare each field
		const fieldsToCompare = ["description", "minimum", "maximum", "type"] as const;
		for (const field of fieldsToCompare) {
			if (localParam[field] !== lfParam[field]) {
				diff.parameterDiffs.push({
					paramName,
					field,
					local: localParam[field],
					langfuse: lfParam[field],
				});
			}
		}

		// Compare enum arrays
		if (JSON.stringify(localParam.enum) !== JSON.stringify(lfParam.enum)) {
			diff.parameterDiffs.push({
				paramName,
				field: "enum",
				local: localParam.enum,
				langfuse: lfParam.enum,
			});
		}
	}

	// Return null if no differences
	if (
		!diff.descriptionDiff &&
		diff.parameterDiffs.length === 0 &&
		diff.missingInLocal.length === 0 &&
		diff.missingInLangfuse.length === 0
	) {
		return null;
	}

	return diff;
}

/**
 * Get comprehensive tool diffs between local and Langfuse
 */
function getToolDiffs(
	localTools: PromptToolDefinition[],
	langfuseTools: PromptToolDefinition[],
): ToolDiff[] {
	const diffs: ToolDiff[] = [];

	for (const lf of langfuseTools) {
		const local = localTools.find((t) => t.function.name === lf.function.name);
		const diff = compareTools(local, lf);
		if (diff) {
			diffs.push(diff);
		}
	}

	return diffs;
}

/**
 * Format a tool diff for display
 */
function formatToolDiff(diff: ToolDiff, verbose = false): string[] {
	const lines: string[] = [];

	lines.push(`  ${pc.cyan(diff.name)}:`);

	if (diff.descriptionDiff) {
		lines.push(`    ${pc.yellow("description")} differs`);
		if (verbose) {
			lines.push(`      ${pc.red("-")} ${diff.descriptionDiff.local.slice(0, 60)}...`);
			lines.push(`      ${pc.green("+")} ${diff.descriptionDiff.langfuse.slice(0, 60)}...`);
		}
	}

	for (const pd of diff.parameterDiffs) {
		lines.push(`    ${pc.yellow(`${pd.paramName}.${pd.field}`)} differs`);
		if (verbose) {
			lines.push(`      ${pc.red("-")} ${JSON.stringify(pd.local)}`);
			lines.push(`      ${pc.green("+")} ${JSON.stringify(pd.langfuse)}`);
		}
	}

	for (const p of diff.missingInLocal) {
		lines.push(`    ${pc.red(`${p}`)} missing in local`);
	}

	for (const p of diff.missingInLangfuse) {
		lines.push(`    ${pc.yellow(`${p}`)} missing in Langfuse`);
	}

	return lines;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Diff Application (uses AST-based functions from ast-utils.ts)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Apply all tool diffs from a ToolDiff to a tool file.
 * Returns list of what was updated.
 */
function applyToolDiff(
	toolDiff: ToolDiff,
	toolFilePath: string,
): { updates: string[]; errors: string[] } {
	const updates: string[] = [];
	const errors: string[] = [];

	// Update tool description if changed
	if (toolDiff.descriptionDiff) {
		const result = updateToolDescriptionInFile(toolFilePath, toolDiff.descriptionDiff.langfuse);
		if (result.error) {
			errors.push(`description: ${result.error}`);
		} else if (result.updated) {
			updates.push("description");
		}
	}

	// Update parameter descriptions if changed
	for (const pd of toolDiff.parameterDiffs) {
		if (pd.field === "description" && typeof pd.langfuse === "string") {
			const result = updateParameterDescriptionInFile(toolFilePath, pd.paramName, pd.langfuse);
			if (result.error) {
				errors.push(`${pd.paramName}.description: ${result.error}`);
			} else if (result.updated) {
				updates.push(`${pd.paramName}.description`);
			}
		} else if (pd.field !== "description") {
			// For non-description fields (minimum, maximum, etc.), we can't easily update
			// the Zod schema, so just warn
			errors.push(
				`${pd.paramName}.${pd.field}: Cannot sync (Zod schema is source of truth for constraints)`,
			);
		}
	}

	return { updates, errors };
}

// ─────────────────────────────────────────────────────────────────────────────
// Commands
// ─────────────────────────────────────────────────────────────────────────────

async function listCommand(): Promise<void> {
	const spinner = ora("Discovering prompts...").start();
	const prompts = await discoverPrompts();
	spinner.stop();

	header("Discovered Prompts");

	if (prompts.length === 0) {
		warning("No prompts found in src/prompts/**/*.prompt.ts");
		console.log();
		return;
	}

	for (const p of prompts) {
		const toolCount = p.tools.length;
		const hasTools = toolCount > 0 ? pc.dim(` [${toolCount} tools]`) : "";
		console.log(`  ${pc.bold(p.definition.name)}${hasTools}`);
		detail(`File: ${p.relativePath}`);
		if (toolCount > 0) {
			detail(`Tools: ${p.tools.map((t) => t.name).join(", ")}`);
		}
		console.log();
	}

	info(`Found ${prompts.length} prompt(s)`);
	console.log();
}

async function statusCommand(): Promise<void> {
	const langfuse = ensureCredentials();
	const spinner = ora("Discovering prompts...").start();

	const prompts = await discoverPrompts();
	spinner.text = "Checking sync status...";

	let outOfSync = 0;

	const results: Array<{
		prompt: DiscoveredPrompt;
		version: number | null;
		promptDiff: boolean;
		toolDiffs: ToolDiff[];
		error?: string;
	}> = [];

	for (const p of prompts) {
		if (p.definition.type !== "text") {
			results.push({
				prompt: p,
				version: null,
				promptDiff: false,
				toolDiffs: [],
				error: "Chat prompts not supported",
			});
			continue;
		}

		try {
			const lf = await langfuse.prompt.get(p.definition.name, {
				label: "production",
				type: "text",
			});
			const lfConfig = (lf.config || {}) as Record<string, unknown>;
			const localTools = (p.definition.config?.tools || []) as PromptToolDefinition[];
			const lfTools = (lfConfig.tools || []) as PromptToolDefinition[];

			results.push({
				prompt: p,
				version: lf.version,
				promptDiff: p.definition.prompt !== lf.prompt,
				toolDiffs: getToolDiffs(localTools, lfTools),
			});
		} catch {
			results.push({
				prompt: p,
				version: null,
				promptDiff: false,
				toolDiffs: [],
				error: "Not in Langfuse",
			});
		}
	}

	spinner.stop();
	header("Sync Status");

	for (const r of results) {
		const name = pc.bold(r.prompt.definition.name);
		const toolCount = r.prompt.tools.length;
		const hasTools = toolCount > 0 ? pc.dim(` [${toolCount} tools]`) : "";

		if (r.error) {
			warning(`${name}${hasTools} ${symbols.arrow} ${r.error}`);
			detail(`File: ${r.prompt.relativePath}`);
			continue;
		}

		const isOutOfSync = r.promptDiff || r.toolDiffs.length > 0;
		if (isOutOfSync) {
			outOfSync++;
			warning(`${name}${hasTools} ${pc.yellow("OUT OF SYNC")} ${pc.dim(`v${r.version}`)}`);
			detail(`File: ${r.prompt.relativePath}`);
			if (r.promptDiff) {
				detail("Prompt differs");
			}
			if (r.toolDiffs.length > 0) {
				// Show detailed tool diffs
				for (const td of r.toolDiffs) {
					const diffParts: string[] = [];
					if (td.descriptionDiff) {
						diffParts.push("description");
					}
					for (const pd of td.parameterDiffs) {
						diffParts.push(`${pd.paramName}.${pd.field}`);
					}
					detail(`${pc.cyan(td.name)}: ${diffParts.join(", ")}`);
				}
			}
		} else {
			success(`${name}${hasTools} ${pc.green("synced")} ${pc.dim(`v${r.version}`)}`);
		}
	}

	console.log();
	if (outOfSync > 0) {
		info(`${outOfSync} out of sync`);
		console.log();
		console.log(`  ${pc.cyan("npm run prompts push")}  ${pc.dim("Push local → Langfuse")}`);
		console.log(`  ${pc.cyan("npm run prompts pull")}  ${pc.dim("Pull Langfuse → local")}`);
	} else {
		success("All synced!");
	}
	console.log();
}

async function diffCommand(): Promise<void> {
	const langfuse = ensureCredentials();
	const spinner = ora("Discovering prompts...").start();

	const prompts = await discoverPrompts();
	spinner.text = "Comparing...";

	spinner.stop();
	header("Differences");

	let hasDiff = false;

	for (const p of prompts) {
		if (p.definition.type !== "text") {
			continue;
		}

		try {
			const lf = await langfuse.prompt.get(p.definition.name, {
				label: "production",
				type: "text",
			});
			const lfConfig = (lf.config || {}) as Record<string, unknown>;
			const localTools = (p.definition.config?.tools || []) as PromptToolDefinition[];
			const lfTools = (lfConfig.tools || []) as PromptToolDefinition[];

			const promptDiff = p.definition.prompt !== lf.prompt;
			const toolDiffs = getToolDiffs(localTools, lfTools);

			if (!promptDiff && toolDiffs.length === 0) {
				success(`${pc.bold(p.definition.name)} ${symbols.arrow} no diff`);
				continue;
			}

			hasDiff = true;
			warning(pc.bold(p.definition.name));

			if (promptDiff) {
				console.log();
				console.log(`  ${pc.yellow("prompt")} differs`);
				const localLines = (p.definition.prompt as string).split("\n").length;
				const lfLines = lf.prompt.split("\n").length;
				console.log(`    ${pc.dim(`${localLines} lines (local) vs ${lfLines} lines (Langfuse)`)}`);
			}

			if (toolDiffs.length > 0) {
				console.log();
				for (const td of toolDiffs) {
					const lines = formatToolDiff(td, true);
					for (const line of lines) {
						console.log(line);
					}
				}
			}
		} catch {
			info(`${pc.bold(p.definition.name)} ${symbols.arrow} not in Langfuse`);
		}
	}

	if (!hasDiff) {
		console.log();
		success("No differences!");
	}
	console.log();
}

async function pushCommand(options: { dryRun: boolean }): Promise<void> {
	const langfuse = ensureCredentials();

	header(options.dryRun ? "Push Preview" : "Pushing to Langfuse");

	const prompts = await discoverPrompts();
	const stats = { updated: 0, unchanged: 0, failed: 0 };

	for (const p of prompts) {
		const name = pc.bold(p.definition.name);

		if (p.definition.type !== "text") {
			info(`${name} ${symbols.arrow} skipped`);
			continue;
		}

		const spinner = ora({ text: `${p.definition.name}...`, indent: 2 }).start();

		try {
			let existing: { prompt: string; config: unknown } | null = null;

			try {
				existing = await langfuse.prompt.get(p.definition.name, {
					label: "production",
					type: "text",
				});
			} catch {
				// Doesn't exist
			}

			if (existing) {
				const promptChanged = existing.prompt !== p.definition.prompt;
				const configChanged = !configsEqual(
					existing.config as Record<string, unknown>,
					p.definition.config,
				);

				if (!(promptChanged || configChanged)) {
					spinner.succeed(`${name} ${pc.dim("unchanged")}`);
					stats.unchanged++;
					continue;
				}

				if (options.dryRun) {
					spinner.info(`${name} ${pc.blue("would update")}`);
				} else {
					await langfuse.prompt.create({
						name: p.definition.name,
						type: "text",
						prompt: p.definition.prompt as string,
						config: p.definition.config,
						labels: p.definition.labels,
						tags: p.definition.tags,
					});
					spinner.succeed(`${name} ${pc.blue("updated")}`);
				}
				stats.updated++;
			} else {
				if (options.dryRun) {
					spinner.info(`${name} ${pc.green("would create")}`);
				} else {
					await langfuse.prompt.create({
						name: p.definition.name,
						type: "text",
						prompt: p.definition.prompt as string,
						config: p.definition.config,
						labels: p.definition.labels,
						tags: p.definition.tags,
					});
					spinner.succeed(`${name} ${pc.green("created")}`);
				}
				stats.updated++;
			}
		} catch (err) {
			spinner.fail(`${name} ${pc.red("failed")}`);
			detail(err instanceof Error ? err.message : String(err));
			stats.failed++;
		}
	}

	summary(stats);

	if (options.dryRun) {
		console.log();
		info("Dry run. Use without --dry-run to apply.");
	}
	console.log();

	if (stats.failed > 0) {
		process.exit(1);
	}
}

async function pullCommand(options: { dryRun: boolean }): Promise<void> {
	const langfuse = ensureCredentials();

	header(options.dryRun ? "Pull Preview" : "Pulling from Langfuse");

	const prompts = await discoverPrompts();
	const stats = { updated: 0, unchanged: 0, failed: 0 };
	const changedFiles: Set<string> = new Set();

	for (const p of prompts) {
		const name = pc.bold(p.definition.name);

		if (p.definition.type !== "text") {
			info(`${name} ${symbols.arrow} skipped`);
			continue;
		}

		const spinner = ora({ text: `${p.definition.name}...`, indent: 2 }).start();

		try {
			const lf = await langfuse.prompt.get(p.definition.name, {
				label: "production",
				type: "text",
			});
			const lfConfig = (lf.config || {}) as Record<string, unknown>;
			const localTools = (p.definition.config?.tools || []) as PromptToolDefinition[];
			const lfTools = (lfConfig.tools || []) as PromptToolDefinition[];

			const promptDiff = p.definition.prompt !== lf.prompt;
			const toolDiffs = getToolDiffs(localTools, lfTools);

			if (!promptDiff && toolDiffs.length === 0) {
				spinner.succeed(`${name} ${pc.dim(`unchanged v${lf.version}`)}`);
				stats.unchanged++;
				continue;
			}

			if (options.dryRun) {
				spinner.info(`${name} ${pc.blue(`would pull v${lf.version}`)}`);
				if (promptDiff) {
					detail(`Would update: ${p.relativePath}`);
				}
				if (toolDiffs.length > 0 && p.tools.length > 0) {
					detail(`Would update ${toolDiffs.length} tool(s)`);
				}
				stats.updated++;
				continue;
			}

			const updates: string[] = [];
			const allErrors: string[] = [];

			// Update prompt
			if (promptDiff) {
				const result = updatePromptInFile(p.filePath, lf.prompt);
				if (result.error) {
					throw new Error(result.error);
				}
				if (result.updated) {
					updates.push("prompt");
					changedFiles.add(p.filePath);
				}
			}

			// Update tools - apply all diffs (description + parameter descriptions)
			if (toolDiffs.length > 0 && p.tools.length > 0) {
				for (const td of toolDiffs) {
					const toolInfo = p.tools.find((t) => t.name === td.name);
					if (!toolInfo) {
						warning(`  Tool '${td.name}' not found in tool files`);
						continue;
					}

					const { updates: toolUpdates, errors: toolErrors } = applyToolDiff(td, toolInfo.filePath);

					if (toolUpdates.length > 0) {
						updates.push(`${td.name}(${toolUpdates.join(", ")})`);
						changedFiles.add(toolInfo.filePath);
					}

					for (const err of toolErrors) {
						allErrors.push(`${td.name}: ${err}`);
					}
				}
			}

			if (updates.length > 0) {
				spinner.succeed(`${name} ${pc.blue(`v${lf.version}`)}`);
				detail(`Updated: ${updates.join(", ")}`);
				for (const err of allErrors) {
					warning(`  ${err}`);
				}
				stats.updated++;
			} else {
				spinner.succeed(`${name} ${pc.dim("unchanged")}`);
				stats.unchanged++;
			}
		} catch (err) {
			spinner.fail(`${name} ${pc.red("failed")}`);
			detail(err instanceof Error ? err.message : String(err));
			stats.failed++;
		}
	}

	summary(stats);

	if (changedFiles.size > 0 && !options.dryRun) {
		console.log();
		info("Updated files:");
		for (const f of changedFiles) {
			console.log(`    ${pc.cyan(path.relative(path.join(import.meta.dirname, ".."), f))}`);
		}
		console.log();
		console.log(`  ${pc.dim("Review:")} ${pc.cyan("git diff")}`);
		console.log(
			`  ${pc.dim("Commit:")} ${pc.cyan('git add -A && git commit -m "chore: sync from Langfuse"')}`,
		);
	}

	if (options.dryRun) {
		console.log();
		info("Dry run. Use without --dry-run to apply.");
	}

	console.log();

	if (stats.failed > 0) {
		process.exit(1);
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// CLI
// ─────────────────────────────────────────────────────────────────────────────

const program = new Command();

program
	.name("prompts")
	.description("Auto-sync prompts with Langfuse (zero config)")
	.version("3.0.0");

program
	.command("status", { isDefault: true })
	.description("Check sync status")
	.action(statusCommand);

program.command("list").description("List discovered prompts").action(listCommand);

program.command("diff").description("Show differences").action(diffCommand);

program
	.command("push")
	.description("Push local → Langfuse")
	.option("-n, --dry-run", "Preview only")
	.action(pushCommand);

program
	.command("pull")
	.description("Pull Langfuse → local")
	.option("-n, --dry-run", "Preview only")
	.action(pullCommand);

program.parse();
