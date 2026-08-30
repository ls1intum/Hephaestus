import { spawnSync } from "node:child_process";

export type Scope = "agents" | "docs" | "full" | "server" | "webapp";
export type Command = readonly [string, ...string[]];

export function environmentWithoutGitRepository(): NodeJS.ProcessEnv {
	const environment = { ...process.env };
	for (const name of [
		"GIT_ALTERNATE_OBJECT_DIRECTORIES",
		"GIT_COMMON_DIR",
		"GIT_DIR",
		"GIT_INDEX_FILE",
		"GIT_OBJECT_DIRECTORY",
		"GIT_PREFIX",
		"GIT_WORK_TREE",
	])
		delete environment[name];
	return environment;
}

export function parseBase(args: string[]): string {
	if (args.length === 0) return "origin/main";
	const [flag, revision] = args;
	if (args.length === 2 && flag === "--base" && revision !== undefined && revision !== "")
		return revision;
	throw new Error("Usage: pnpm run check:affected [--base <revision>]");
}

const fullGateInputs = [
	/^\.github\//,
	/^\.agents\//,
	/^\.claude\//,
	/^\.changeset\//,
	/^scripts\//,
	/^package\.json$/,
	/^pnpm-lock\.yaml$/,
	/^pnpm-workspace\.yaml$/,
	/^patches\//,
	/^tsconfig(?:\.agents)?\.json$/,
	/^\.ox(?:fmt|lint)rc\.json$/,
	/^server\/openapi\.yaml$/,
	/^webapp\/src\/api\//,
	/^webapp\/src\/routeTree\.gen\.ts$/,
	/^webapp\/tools\/oxlint\//,
	/^docs\/contributor\/erd\/schema\.mmd$/,
	/(?:^|\/)AGENTS\.md$/,
	/(?:^|\/)CLAUDE\.md$/,
];

export function scopesFor(paths: string[]): Scope[] {
	if (paths.some((path) => fullGateInputs.some((pattern) => pattern.test(path)))) return ["full"];
	const scopes = new Set<Scope>();
	for (const path of paths) {
		if (path.startsWith("docs/images/readme/")) {
			scopes.add("docs");
			scopes.add("webapp");
		} else if (path.startsWith("webapp/")) scopes.add("webapp");
		else if (path.startsWith("server/")) {
			if (/\/resources\/(?:agent|practices\/precompute)\//.test(path)) scopes.add("agents");
			else scopes.add("server");
		} else if (path.startsWith("docker/agents/")) scopes.add("agents");
		else if (path.startsWith("docs/")) scopes.add("docs");
		else return ["full"];
	}
	return [...scopes].toSorted();
}

function git(cwd: string, ...args: string[]): string[] {
	const result = spawnSync("git", args, {
		cwd,
		encoding: "utf8",
		env: environmentWithoutGitRepository(),
	});
	if (result.status !== 0)
		throw new Error(`git ${args.join(" ")} failed: ${result.stderr.trim() || "unknown error"}`);
	return result.stdout.split("\n").filter(Boolean);
}

export function changedPaths(requestedBase: string, cwd = process.cwd()): string[] {
	const base = git(cwd, "merge-base", "HEAD", requestedBase)[0];
	if (base === undefined) throw new Error(`No merge base with ${requestedBase}`);
	return [
		...new Set([
			...git(cwd, "diff", "--no-renames", "--name-only", `${base}...HEAD`),
			...git(cwd, "diff", "--no-renames", "--name-only"),
			...git(cwd, "diff", "--cached", "--no-renames", "--name-only"),
			...git(cwd, "ls-files", "--others", "--exclude-standard"),
		]),
	].toSorted();
}

export function commandsFor(scopes: Scope[]): Command[] {
	if (scopes.includes("full")) return [["vp", "run", "quality"]];
	const commands: Record<Exclude<Scope, "full">, Command> = {
		agents: ["vp", "run", "affected:agents"],
		docs: ["vp", "run", "affected:docs"],
		server: ["vp", "run", "affected:server"],
		webapp: ["vp", "run", "affected:webapp"],
	};
	return scopes.map((scope) => {
		if (scope === "full") throw new Error("Full scope must override scoped commands");
		return commands[scope];
	});
}

function run(command: Command): void {
	const [executable, ...arguments_] = command;
	const result = spawnSync(executable, arguments_, { stdio: "inherit" });
	if (result.status !== 0) process.exit(result.status ?? 1);
}

function main(): void {
	const requestedBase = parseBase(process.argv.slice(2));
	const scopes = scopesFor(changedPaths(requestedBase));
	if (scopes.length === 0) {
		console.log("No changes detected; no checks ran.");
		return;
	}
	if (scopes.includes("full"))
		console.log(
			"Shared, generated, or unknown input changed; expanding to the complete local quality gate.",
		);
	else
		console.log(
			`Running affected checks for: ${scopes.join(", ")}. This is not the complete local gate.`,
		);
	for (const command of commandsFor(scopes)) run(command);
	if (scopes.includes("full")) console.log("Complete local quality gate passed.");
	else
		console.log(
			"Affected checks passed. The complete local gate has not run; use `pnpm run check` before pushing.",
		);
}

if (import.meta.main) main();
