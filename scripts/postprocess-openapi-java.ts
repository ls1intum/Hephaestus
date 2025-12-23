#!/usr/bin/env node
/**
 * Post-processes OpenAPI-generated Java files to update version comments.
 * Replaces hardcoded version strings with a reference to the source file.
 */
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, "..");
const TARGET_DIR = path.join(
	REPO_ROOT,
	"server",
	"application-server",
	"src",
	"main",
	"java",
	"de",
	"tum",
	"in",
	"www1",
	"hephaestus",
	"intelligenceservice",
);

const REPLACEMENT_TEXT =
	"The version of the OpenAPI document is defined in server/intelligence-service/openapi.yaml.";

const VERSION_PATTERN = /^\s*\* The version of the OpenAPI document.*$/gm;

/**
 * Recursively walks a directory and returns all Java file paths.
 */
async function walkJavaFiles(dir: string): Promise<string[]> {
	const entries = await fs.readdir(dir, { withFileTypes: true });
	const files = await Promise.all(
		entries.map(async (entry): Promise<string[]> => {
			const fullPath = path.join(dir, entry.name);
			if (entry.isDirectory()) {
				return walkJavaFiles(fullPath);
			}
			if (entry.isFile() && entry.name.endsWith(".java")) {
				return [fullPath];
			}
			return [];
		}),
	);
	return files.flat();
}

/**
 * Processes a single Java file, replacing version comments if found.
 * @returns true if the file was modified, false otherwise.
 */
async function processFile(filePath: string): Promise<boolean> {
	const content = await fs.readFile(filePath, "utf8");

	if (!content.includes("The version of the OpenAPI document:")) {
		return false;
	}

	const updated = content.replace(VERSION_PATTERN, ` * ${REPLACEMENT_TEXT}`);

	if (updated === content) {
		return false;
	}

	await fs.writeFile(filePath, updated, "utf8");
	return true;
}

/**
 * Checks if a directory exists.
 */
async function directoryExists(dir: string): Promise<boolean> {
	try {
		const stats = await fs.stat(dir);
		return stats.isDirectory();
	} catch {
		return false;
	}
}

async function main(): Promise<void> {
	if (!(await directoryExists(TARGET_DIR))) {
		console.warn(`Target directory not found: ${TARGET_DIR}`);
		return;
	}

	const javaFiles = await walkJavaFiles(TARGET_DIR);
	let changedCount = 0;

	for (const file of javaFiles) {
		if (await processFile(file)) {
			changedCount += 1;
		}
	}

	console.log(
		`Processed ${javaFiles.length} Java files. Updated ${changedCount} files.`,
	);
}

main().catch((error: unknown) => {
	console.error(error);
	process.exitCode = 1;
});
