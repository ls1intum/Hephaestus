import { execFile } from "node:child_process";
import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const REPO_ROOT = resolve(import.meta.dirname, "..");
const JAVA_SOURCE = /^server\/application\/src\/(?:main|test)\/java\/.*\.java$/;

export interface JavaSource {
	readonly path: string;
	readonly content: string;
}

export function isHandwrittenJavaSource(path: string): boolean {
	return JAVA_SOURCE.test(path);
}

export async function discoverJavaSourcePaths(
	root: string = REPO_ROOT,
): Promise<readonly string[]> {
	const { stdout } = await execFileAsync(
		"git",
		["ls-files", "-z", "--cached", "--others", "--exclude-standard"],
		{
			cwd: root,
			env: {
				...process.env,
				GIT_DIR: undefined,
				GIT_INDEX_FILE: undefined,
				GIT_WORK_TREE: undefined,
			},
		},
	);
	const candidates = stdout.split("\0").filter(isHandwrittenJavaSource);
	const present = await Promise.all(
		candidates.map(async (path) => {
			try {
				await access(resolve(root, path));
				return path;
			} catch (error) {
				if (isNodeError(error) && error.code === "ENOENT") return undefined;
				throw error;
			}
		}),
	);
	const paths = present.filter((path): path is string => path !== undefined);
	if (paths.length === 0) {
		throw new Error(
			"No handwritten Java sources found under server/application/src/{main,test}/java; refusing to pass without checking anything.",
		);
	}
	return paths;
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
	return error instanceof Error;
}

function unicodeEscapes(source: string): string {
	return source.replace(/\\u+([0-9a-fA-F]{4})/g, (_, hex: string) =>
		String.fromCharCode(Number.parseInt(hex, 16)),
	);
}

function suppressionBodies(source: string): readonly string[] {
	const text = unicodeEscapes(source);
	const bodies: string[] = [];
	const annotation = /@(?:java\.lang\.)?SuppressWarnings\s*\(/g;
	for (let match = annotation.exec(text); match !== null; match = annotation.exec(text)) {
		const start = annotation.lastIndex;
		let depth = 1;
		let quoted = false;
		let escaped = false;
		for (let index = start; index < text.length; index++) {
			const character = text[index];
			if (quoted) {
				if (escaped) escaped = false;
				else if (character === "\\") escaped = true;
				else if (character === '"') quoted = false;
				continue;
			}
			if (character === '"') quoted = true;
			else if (character === "(") depth++;
			else if (character === ")" && --depth === 0) {
				bodies.push(text.slice(start, index));
				annotation.lastIndex = index + 1;
				break;
			}
		}
	}
	return bodies;
}

function stringValues(body: string): string {
	return [...body.matchAll(/"((?:\\.|[^"\\])*)"/g)]
		.map((match) => match[1]?.replaceAll(/\\(["\\])/g, "$1") ?? "")
		.join("");
}

function violatesPolicy(body: string): boolean {
	if (stringValues(body).includes("NullAway")) return true;
	const withoutStrings = body.replaceAll(/"(?:\\.|[^"\\])*"/g, "");
	return !/^[\s{},+]*$/.test(withoutStrings);
}

export function nullnessPolicyViolations(sources: readonly JavaSource[]): readonly string[] {
	return sources
		.filter(({ content }) => suppressionBodies(content).some(violatesPolicy))
		.map(({ path }) => path);
}

async function main(): Promise<void> {
	const paths = await discoverJavaSourcePaths();
	const sources: JavaSource[] = [];
	for (const path of paths) {
		sources.push({ path, content: await readFile(resolve(REPO_ROOT, path), "utf8") });
	}
	const suppressed = nullnessPolicyViolations(sources);
	if (suppressed.length > 0) {
		throw new Error(
			`NullAway suppressions and indirect suppression names are forbidden; fix the contract or implementation:\n${suppressed.map((path) => `  ${path}`).join("\n")}`,
		);
	}
	console.log(
		`Java nullness policy: ${sources.length} handwritten source file(s), no NullAway suppressions.`,
	);
}

if (import.meta.main) await main();
