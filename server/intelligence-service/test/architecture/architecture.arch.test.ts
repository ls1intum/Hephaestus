/**
 * Architecture Tests
 *
 * These tests enforce architectural rules and coding standards.
 * They catch violations early before they become tech debt.
 *
 * Run with: npm run test:arch
 */
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

// Resolve to the actual src directory (two levels up from test/architecture/)
const SRC_DIR = path.resolve(__dirname, "../../src");

/**
 * Recursively get all TypeScript files in a directory
 */
function getTypeScriptFiles(dir: string): string[] {
	const files: string[] = [];

	function walk(currentDir: string) {
		const entries = fs.readdirSync(currentDir, { withFileTypes: true });
		for (const entry of entries) {
			const fullPath = path.join(currentDir, entry.name);
			if (entry.isDirectory()) {
				walk(fullPath);
			} else if (entry.name.endsWith(".ts") && !entry.name.endsWith(".d.ts")) {
				files.push(fullPath);
			}
		}
	}

	walk(dir);
	return files;
}

/**
 * Check if a file contains a pattern
 */
function fileContains(filePath: string, pattern: RegExp): boolean {
	const content = fs.readFileSync(filePath, "utf-8");
	return pattern.test(content);
}

/**
 * Get all lines matching a pattern with line numbers
 */
function findPatternMatches(
	filePath: string,
	pattern: RegExp,
): { line: number; content: string }[] {
	const content = fs.readFileSync(filePath, "utf-8");
	const lines = content.split("\n");
	const matches: { line: number; content: string }[] = [];

	lines.forEach((line, index) => {
		if (pattern.test(line)) {
			matches.push({ line: index + 1, content: line.trim() });
		}
	});

	return matches;
}

describe("Architecture: Layer Dependencies", () => {
	const srcFiles = getTypeScriptFiles(SRC_DIR);

	it("routes must NOT import directly from @/db (use repositories)", () => {
		const routeFiles = srcFiles.filter((f) => f.includes("/routes/"));
		const violations: string[] = [];

		for (const file of routeFiles) {
			if (fileContains(file, /from ["']@\/db["']/)) {
				violations.push(path.relative(SRC_DIR, file));
			}
		}

		expect(
			violations,
			`Routes should use repository pattern, not direct DB access:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});

	it("services must NOT import from routes (no circular dependencies)", () => {
		const serviceFiles = srcFiles.filter((f) => f.includes("/services/"));
		const violations: string[] = [];

		for (const file of serviceFiles) {
			if (fileContains(file, /from ["']@\/routes/)) {
				violations.push(path.relative(SRC_DIR, file));
			}
		}

		expect(
			violations,
			`Services must not depend on routes:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});

	it("middleware must NOT import from routes", () => {
		const middlewareFiles = srcFiles.filter((f) => f.includes("/middleware/"));
		const violations: string[] = [];

		for (const file of middlewareFiles) {
			if (fileContains(file, /from ["']@\/routes/)) {
				violations.push(path.relative(SRC_DIR, file));
			}
		}

		expect(
			violations,
			`Middleware must not depend on routes:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});

	it("repositories must NOT import from routes or services", () => {
		const repoFiles = srcFiles.filter((f) => f.includes("/repositories/"));
		const violations: string[] = [];

		for (const file of repoFiles) {
			if (fileContains(file, /from ["']@\/(routes|services)/)) {
				violations.push(path.relative(SRC_DIR, file));
			}
		}

		expect(
			violations,
			`Repositories must be pure data access layer:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});

	it("should use @/ alias for internal imports (no deep relative paths)", () => {
		const violations: string[] = [];
		// Allowlist: openapi.ts needs to import package.json from webapp
		const allowlist = ["config/openapi.ts"];

		for (const file of srcFiles) {
			const relativePath = path.relative(SRC_DIR, file);
			if (allowlist.some((allowed) => relativePath.endsWith(allowed))) {
				continue;
			}
			if (fileContains(file, /from ["']\.\.\/\.\.\//)) {
				violations.push(relativePath);
			}
		}

		expect(
			violations,
			`Use @/ alias instead of deep relative imports:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});
});

describe("Architecture: Naming Conventions", () => {
	const srcFiles = getTypeScriptFiles(SRC_DIR);

	it("handler files should use singular .handler.ts suffix", () => {
		const violations = srcFiles
			.filter((f) => f.includes(".handlers.ts"))
			.map((f) => path.relative(SRC_DIR, f));

		expect(
			violations,
			`Use singular .handler.ts suffix:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});

	it("schema files should use singular .schema.ts suffix", () => {
		const violations = srcFiles
			.filter((f) => f.includes(".schemas.ts"))
			.map((f) => path.relative(SRC_DIR, f));

		expect(
			violations,
			`Use singular .schema.ts suffix:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});
});

describe("Architecture: Code Quality", () => {
	const srcFiles = getTypeScriptFiles(SRC_DIR).filter(
		(f) => !(f.includes("/db/schema.ts") || f.includes("/db/relations.ts")), // Generated files
	);

	it("must NOT have console.log in production code (use pino logger)", () => {
		const violations: { file: string; line: number; content: string }[] = [];

		for (const file of srcFiles) {
			// Allow console in scripts directory
			if (file.includes("/scripts/")) {
				continue;
			}

			const matches = findPatternMatches(file, /console\.(log|debug|info|warn|error)\(/);
			for (const match of matches) {
				violations.push({
					file: path.relative(SRC_DIR, file),
					line: match.line,
					content: match.content,
				});
			}
		}

		expect(
			violations,
			`Found console.* calls - use pino logger:\n${violations.map((v) => `  ${v.file}:${v.line} - ${v.content}`).join("\n")}`,
		).toHaveLength(0);
	});

	it("handler functions should be async", () => {
		const handlerFiles = srcFiles.filter((f) => f.includes(".handler.ts"));
		const violations: string[] = [];

		for (const file of handlerFiles) {
			const content = fs.readFileSync(file, "utf-8");
			// Check for non-async handler exports (synchronous arrow functions)
			// Pattern: export const xyzHandler = (c) => { without async
			const syncHandlerPattern = /export const \w+Handler\s*[^=]*=\s*(?!async)\([^)]*\)\s*=>/;
			if (syncHandlerPattern.test(content)) {
				violations.push(path.relative(SRC_DIR, file));
			}
		}

		expect(
			violations,
			`Handlers should be async functions:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});

	it("should limit TODO comments (max 5 allowed)", () => {
		const todos: { file: string; line: number; content: string }[] = [];

		for (const file of srcFiles) {
			const matches = findPatternMatches(file, /\bTODO\b/i);
			for (const match of matches) {
				todos.push({
					file: path.relative(SRC_DIR, file),
					line: match.line,
					content: match.content,
				});
			}
		}

		expect(
			todos.length,
			`Found ${todos.length} TODOs (max 5 allowed). Create issues instead:\n${todos.map((t) => `  ${t.file}:${t.line}`).join("\n")}`,
		).toBeLessThanOrEqual(5);
	});
});

describe("Architecture: Security", () => {
	const srcFiles = getTypeScriptFiles(SRC_DIR);

	it("must NOT have hardcoded secrets", () => {
		const secretPatterns = [
			{ pattern: /password\s*=\s*["'][^"']{8,}["']/i, name: "password" },
			{ pattern: /api[_-]?key\s*=\s*["'][^"']{16,}["']/i, name: "api key" },
			{ pattern: /secret\s*=\s*["'][^"']{16,}["']/i, name: "secret" },
			{ pattern: /token\s*=\s*["'][a-zA-Z0-9]{20,}["']/i, name: "token" },
			{ pattern: /private[_-]?key\s*=\s*["'][^"']+["']/i, name: "private key" },
		];

		const violations: string[] = [];

		for (const file of srcFiles) {
			const content = fs.readFileSync(file, "utf-8");

			// Skip if file is about env configuration
			if (file.includes("env.ts")) {
				continue;
			}

			for (const { pattern, name } of secretPatterns) {
				if (pattern.test(content)) {
					// Check it's not referencing env vars or is a type definition
					if (
						!(
							content.includes("process.env") ||
							content.includes("env.") ||
							content.includes(": string")
						)
					) {
						violations.push(`${path.relative(SRC_DIR, file)}: possible ${name}`);
					}
				}
			}
		}

		expect(violations, `Potential hardcoded secrets:\n  ${violations.join("\n  ")}`).toHaveLength(
			0,
		);
	});

	it("must NOT expose stack traces in error responses", () => {
		const violations: { file: string; line: number }[] = [];

		for (const file of srcFiles) {
			if (!file.includes("/routes/")) {
				continue;
			}

			const matches = findPatternMatches(file, /error:\s*err\.(message|stack)/);
			for (const match of matches) {
				violations.push({
					file: path.relative(SRC_DIR, file),
					line: match.line,
				});
			}
		}

		expect(
			violations,
			`Error responses must not expose stack traces:\n${violations.map((v) => `  ${v.file}:${v.line}`).join("\n")}`,
		).toHaveLength(0);
	});
});

describe("Architecture: File Organization", () => {
	it("index.ts files should only contain exports (barrel files)", () => {
		// Exclude: routes (have handlers), db (generated), and the root src/index.ts (app entry point)
		const indexFiles = getTypeScriptFiles(SRC_DIR).filter(
			(f) =>
				f.endsWith("/index.ts") &&
				!f.includes("/routes/") &&
				!f.includes("/db/") &&
				f !== path.join(SRC_DIR, "index.ts"), // Exclude app entry point
		);
		const violations: string[] = [];

		for (const file of indexFiles) {
			const content = fs.readFileSync(file, "utf-8");
			// Check if file contains function definitions (not just exports)
			const hasFunctionDef = /^(export\s+)?(async\s+)?function\s+\w+/m.test(content);
			const hasClassDef = /^(export\s+)?class\s+\w+/m.test(content);

			if (hasFunctionDef || hasClassDef) {
				violations.push(path.relative(SRC_DIR, file));
			}
		}

		expect(
			violations,
			`Barrel index.ts files should only re-export:\n  ${violations.join("\n  ")}`,
		).toHaveLength(0);
	});
});
