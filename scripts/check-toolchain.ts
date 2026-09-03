/**
 * The JavaScript toolchain has one driver and one package-manager lane (ADR 0040): Vite+ (`vp`)
 * runs every task, pnpm installs behind `vp install`, and Node stays pinned by the repository. This
 * gate fails when a pin, a bundled tool version, a setup step or a command anywhere in the tree
 * drifts from that shape.
 */
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";

import packageArgument from "npm-package-arg";
import { parse } from "yaml";

import { asRecord, isRecord } from "./lib/json.ts";
import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";
import { commandsOf, loadTasks } from "./lib/task-graph.ts";
import { BUNDLED_PINS, bundledVersions, CATALOG_FILE } from "./lib/toolchain-pins.ts";

function readJson(file: string): Record<string, unknown> {
	return asRecord(JSON.parse(readFileSync(file, "utf8")), file);
}

const manifest = readJson("package.json");
const { devEngines, engines, packageManager } = manifest;
const match =
	typeof packageManager === "string" ? /^pnpm@(\d+\.\d+\.\d+)$/.exec(packageManager) : null;
if (!match) throw new Error("package.json#packageManager must pin an exact pnpm version");
const version = match[1] ?? "";
const runtime = isRecord(devEngines) ? devEngines.runtime : undefined;
const manager = isRecord(devEngines) ? devEngines.packageManager : undefined;
// The Node major is a decision (ADR 0036, ADR 0037); the patch release is Renovate's to move.
if (
	!isRecord(runtime) ||
	runtime.name !== "node" ||
	typeof runtime.version !== "string" ||
	!/^24\.\d+\.\d+$/.test(runtime.version) ||
	runtime.onFail !== "error"
)
	throw new Error("package.json#devEngines.runtime must pin an exact Node 24 release");
const nodeVersion: string = runtime.version;
// Renovate provisions the Node it updates the lockfile with from `engines`, so that field restates
// the exact pin.
if (!isRecord(engines) || engines.node !== nodeVersion)
	throw new Error(`package.json#engines.node must pin Node ${nodeVersion}`);
if (
	!isRecord(manager) ||
	manager.name !== "pnpm" ||
	manager.version !== version ||
	manager.onFail !== "error"
)
	throw new Error(`package.json#devEngines.packageManager must pin pnpm ${version}`);

// Vite+ drives every task and downloads the pinned pnpm behind `vp install`.
const devDependencies = manifest.devDependencies;
if (!isRecord(devDependencies)) throw new Error("package.json must declare devDependencies");
const vitePlusPin = devDependencies["vite-plus"];
if (typeof vitePlusPin !== "string" || !/^\d+\.\d+\.\d+$/.test(vitePlusPin))
	throw new Error("package.json#devDependencies.vite-plus must pin an exact version");
const vitePlus: string = vitePlusPin;
const huskyFiles = execFileSync("git", ["ls-files", ".husky"], { encoding: "utf8" }).trim();
if ("husky" in devDependencies || huskyFiles !== "")
	throw new Error("Git hooks run through the Vite+ dispatcher; remove husky");
const scripts = manifest.scripts;
if (!isRecord(scripts) || scripts.prepare !== "node scripts/enable-hooks.ts")
	throw new Error("package.json#scripts.prepare must enable the Vite+ hook dispatcher");
for (const hook of ["commit-msg", "pre-push"]) {
	const file = `.vite-hooks/${hook}`;
	// Git carries the executable bit; a Windows checkout does not, so the index is the answer.
	const indexed = execFileSync("git", ["ls-files", "-s", file], { encoding: "utf8" });
	if (!indexed.startsWith("100755 ")) throw new Error(`${file} must be committed with mode 100755`);
}

// Vite+ bundles its tools at exact versions; a direct pin that drifts from the bundle would run one
// version through `vp` and another through the editor or a package script. The catalog is the one
// place the bundled versions are restated, and every manifest pins those tools through it.
const catalog = asRecord(
	asRecord(parse(readFileSync(CATALOG_FILE, "utf8")), CATALOG_FILE).catalog,
	`${CATALOG_FILE}#catalog`,
);
for (const [name, pin] of Object.entries(bundledVersions())) {
	if (catalog[name] !== pin)
		throw new Error(
			`${CATALOG_FILE}#catalog.${name} must be ${pin}, the version vite-plus ${vitePlus} bundles; found ${String(catalog[name])}. Run: vp run sync:toolchain-pins`,
		);
}

// The root manifest keeps one script, the pnpm lifecycle hook; every other command is a task.
if (!isDeepStrictEqual(Object.keys(scripts), ["prepare"]))
	throw new Error(
		"package.json#scripts must contain only prepare; commands live in vite.config.ts",
	);
// A root-level config file is formatted by name, so a new one must be added to `format:config`.
const tasks = await loadTasks();
const configFormat = new Set(commandsOf(tasks["format:config"]).join(" ").split(" "));
const trackedConfig = execFileSync(
	"git",
	["ls-files", "-z", "*.json", "*.ts", ".vscode/*.json", ".changeset/*.cjs", ".changeset/*.json"],
	{ encoding: "utf8", maxBuffer: CAPTURE_LIMIT_BYTES },
)
	.split("\0")
	.filter((file) => file !== "" && (!file.includes("/") || /^\.(?:vscode|changeset)\//.test(file)));
for (const file of [...trackedConfig, "scripts/tsconfig.json", "project.code-workspace"])
	if (!configFormat.has(file)) throw new Error(`${file} is not in the format:config file set`);
const usesTsx = (command: string): boolean => /(?:^|[;&|])\s*tsx(?:\s|$)/.test(command);
for (const [name, task] of Object.entries(tasks)) {
	for (const line of commandsOf(task))
		if (usesTsx(line)) throw new Error(`vite.config.ts task ${name} must use Node directly`);
}
for (const file of [
	"package.json",
	"docs/package.json",
	"webapp/package.json",
	"docker/agents/pi/package.json",
]) {
	const workspaceManifest = readJson(file);
	const workspaceScripts = workspaceManifest.scripts;
	if (isRecord(workspaceScripts))
		for (const [name, command] of Object.entries(workspaceScripts)) {
			if (typeof command === "string" && usesTsx(command))
				throw new Error(`${file}#scripts.${name} must use Node directly`);
		}
	for (const section of [
		"dependencies",
		"devDependencies",
		"optionalDependencies",
		"peerDependencies",
	]) {
		const dependencies = workspaceManifest[section];
		if (!isRecord(dependencies)) continue;
		for (const [name, specifier] of Object.entries(dependencies)) {
			if (name in BUNDLED_PINS && specifier !== "catalog:")
				throw new Error(`${file}#${section}.${name} must pin through the catalog`);
			if (specifier === "catalog:") continue;
			if (typeof specifier !== "string" || !packageArgument.resolve(name, specifier).registry)
				throw new Error(`${file} uses unsupported dependency ${name}@${String(specifier)}`);
		}
	}
}
for (const required of [
	"pnpm-lock.yaml",
	"pnpm-workspace.yaml",
	".github/actions/setup-toolchain/action.yml",
]) {
	if (!existsSync(required)) throw new Error(`${required} is required`);
}

// pnpm reads its install policy from pnpm-workspace.yaml, so the file is the config.
const workspace = asRecord(
	parse(readFileSync("pnpm-workspace.yaml", "utf8")),
	"pnpm-workspace.yaml",
);
function expectConfig(name: string, expected: unknown): void {
	const actual = workspace[name];
	if (!isDeepStrictEqual(actual, expected)) {
		throw new Error(
			`pnpm-workspace.yaml ${name} must be ${JSON.stringify(expected)}, found ${JSON.stringify(actual)}`,
		);
	}
}
expectConfig("packages", [".", "webapp", "docs"]);
expectConfig("nodeLinker", "isolated");
expectConfig("hoist", false);
expectConfig("publicHoistPattern", [
	"react",
	"react-dom",
	"@types/react",
	"@types/react-dom",
	"@types/hast",
]);
const releaseAge = 4320;
expectConfig("minimumReleaseAge", releaseAge);
// The agent image is its own pnpm project: nothing the root pins reaches it unless it is restated.
const imageWorkspace = asRecord(
	parse(readFileSync("docker/agents/pi/pnpm-workspace.yaml", "utf8")),
	"docker/agents/pi/pnpm-workspace.yaml",
);
if (imageWorkspace.minimumReleaseAge !== releaseAge)
	throw new Error(`docker/agents/pi/pnpm-workspace.yaml must set minimumReleaseAge ${releaseAge}`);
const imageEngines = readJson("docker/agents/pi/package.json").engines;
if (!isRecord(imageEngines) || imageEngines.pnpm !== version)
	throw new Error(`docker/agents/pi/package.json must pin pnpm ${version} through engines`);
expectConfig("allowBuilds", {
	"@nestjs/core": false,
	protobufjs: false,
	"core-js": false,
	"@openapitools/openapi-generator-cli": false,
	msw: false,
	esbuild: true,
	"@google/genai": false,
	"@swc/core": true,
	"@sentry/cli": true,
});
const overrides = workspace.overrides;
if (!isRecord(overrides)) throw new Error("pnpm-workspace.yaml must declare overrides");
for (const name of Object.keys(overrides).filter((entry) => entry in BUNDLED_PINS)) {
	if (overrides[name] !== "catalog:")
		throw new Error(`pnpm-workspace.yaml overrides.${name} must pin through the catalog`);
}

// The JDK line is stated once, in `.java-version`; every workflow provisions from it and the pom
// compiles for it.
const javaVersion = readFileSync(".java-version", "utf8").trim();
const pomJava = /<java\.version>(\d+)<\/java\.version>/.exec(
	readFileSync("server/pom.xml", "utf8"),
)?.[1];
if (!/^\d+$/.test(javaVersion) || javaVersion !== pomJava)
	throw new Error(
		`.java-version must state the JDK line server/pom.xml compiles for (${pomJava ?? "unset"})`,
	);
for (const file of execFileSync("git", ["ls-files", ".github"], { encoding: "utf8" }).split("\n")) {
	if (!/\.ya?ml$/.test(file)) continue;
	const source = readFileSync(file, "utf8");
	if (/^\s*java-version:/m.test(source))
		throw new Error(`${file} pins a JDK inline; use java-version-file: .java-version`);
}

// CI provisions Node and pnpm from the manifest pins and installs through the package manager
// without lifecycle scripts; Vite+ is added at its pin and never manages the runtime.
const setup = asRecord(
	parse(readFileSync(".github/actions/setup-toolchain/action.yml", "utf8")),
	"setup-toolchain",
);
const setupSteps = asRecord(setup.runs, "setup-toolchain#runs").steps;
if (!Array.isArray(setupSteps)) throw new Error("setup-toolchain must declare steps");
const setupStep = (
	predicate: (step: Record<string, unknown>) => boolean,
): Record<string, unknown> => {
	const step = setupSteps.filter(isRecord).find(predicate);
	if (!step) throw new Error("setup-toolchain is missing a step it must have");
	return step;
};
const usesAction = (name: string) => (step: Record<string, unknown>) =>
	typeof step.uses === "string" && step.uses.startsWith(`${name}@`);
const withOf = (step: Record<string, unknown>): Record<string, unknown> =>
	asRecord(step.with, "setup-toolchain step inputs");
const installInput = asRecord(
	asRecord(setup.inputs, "setup-toolchain#inputs").install,
	"install input",
);
if (installInput.default !== "none")
	throw new Error("setup-toolchain must install nothing unless a job asks");
if (withOf(setupStep(usesAction("actions/setup-node")))["node-version-file"] !== "package.json")
	throw new Error("setup-toolchain must provision package.json#devEngines.runtime");
const pnpmSetup = withOf(setupStep(usesAction("pnpm/setup")));
if (pnpmSetup.install !== false || pnpmSetup["require-lockfile"] !== true)
	throw new Error(
		"setup-toolchain must leave the install to its own step and require the lockfile",
	);
const vpSetup = withOf(setupStep(usesAction("voidzero-dev/setup-vp")));
if (vpSetup["node-manager"] !== false || vpSetup["run-install"] !== false)
	throw new Error("setup-toolchain must let setup-vp neither manage Node nor install");
const frozenInstall = setupStep(
	(step) => step.run === "pnpm install --frozen-lockfile --ignore-scripts",
);
if (frozenInstall.if !== "inputs.install == 'frozen'")
	throw new Error(
		"setup-toolchain must install from the lockfile without scripts, only when asked",
	);
setupStep((step) => step.if === "inputs.install != 'none' && inputs.install != 'frozen'");
for (const step of setupSteps.filter(isRecord)) {
	const inputs = isRecord(step.with) ? step.with : {};
	if ("version" in inputs || "runtime" in inputs)
		throw new Error("setup-toolchain must read tool versions from package.json");
}

const dockerfile = readFileSync("webapp/Dockerfile", "utf8");
// No lifecycle script runs in the image: the platform binaries come from optional dependencies,
// and the root `prepare` would look for a Git repository that an image build does not have.
if (
	!/^\s+pnpm install --offline --frozen-lockfile --ignore-scripts --filter webapp$/m.test(
		dockerfile,
	)
) {
	throw new Error(
		"webapp/Dockerfile must install offline from the lockfile without lifecycle scripts",
	);
}
const agentDockerfile = readFileSync("docker/agents/pi/Dockerfile", "utf8");
// One tag over two digests is two different pnpm builds wearing a single version number.
const pnpmImage = new RegExp(
	`ghcr\\.io/pnpm/pnpm:${version.replaceAll(".", "\\.")}@(sha256:[a-f0-9]{64})`,
);
const pnpmDigests = new Set(
	(
		[
			["webapp/Dockerfile", dockerfile],
			["docker/agents/pi/Dockerfile", agentDockerfile],
		] as const
	).map(([file, content]) => {
		const digest = pnpmImage.exec(content)?.[1];
		if (!digest) throw new Error(`${file} must use the digest-pinned pnpm ${version} image`);
		return digest;
	}),
);
if (pnpmDigests.size !== 1) throw new Error("both Dockerfiles must pin one pnpm base digest");
// Those settings only bind if the image copies them and lets pnpm read them.
if (
	!/^COPY pi\/package\.json pi\/pnpm-lock\.yaml pi\/pnpm-workspace\.yaml \.\/$/m.test(
		agentDockerfile,
	) ||
	!/^RUN pnpm install --prod --frozen-lockfile --ignore-scripts$/m.test(agentDockerfile)
) {
	throw new Error(
		"docker/agents/pi/Dockerfile must install from its own workspace settings, without lifecycle scripts",
	);
}
if (
	!new RegExp(`^ARG NODE_VERSION=${nodeVersion.replaceAll(".", "\\.")}$`, "m").test(dockerfile) ||
	!new RegExp(["^RUN pnpm runtime set node \\$", "\\{NODE_VERSION\\} -g$"].join(""), "m").test(
		dockerfile,
	)
) {
	throw new Error(`webapp/Dockerfile must install Node ${nodeVersion} through pnpm`);
}

// Spelled so that this file passes the scan below.
const retired = ["b", "un"].join("");
const lane = ["pn", "pm"].join("");
const isHistorical = (file: string): boolean =>
	file === "CHANGELOG.md" || file === "MIGRATION.md" || file.startsWith("docs/decisions/");
// The image builds have no `vp`: they install and build through the package-manager lane alone.
const isImageBuild = (file: string): boolean => file.endsWith("Dockerfile");
const tracked = execFileSync("git", ["ls-files", "-z"], {
	encoding: "utf8",
	maxBuffer: CAPTURE_LIMIT_BYTES,
})
	.split("\0")
	.filter(Boolean);
const forbiddenWord = new RegExp(`\\b${retired}\\b`, "i");
const forbiddenPath = new RegExp(
	`(?:^|/)(?:${retired}\\.lockb?|${retired}fig\\.toml|setup-${retired})(?:$|/)`,
	"i",
);
// Package scripts, tasks, hooks, workflows and documents all run and name commands through `vp`.
const forbiddenCommand = new RegExp(`\\b${lane} (?:run|exec|changeset|--filter)\\b|"${lane}", \\[`);
for (const file of tracked) {
	if (isHistorical(file) || !existsSync(file)) continue;
	if (forbiddenPath.test(file)) throw new Error(`${file} is a retired tool artifact`);
	const basename = file.split("/").at(-1) ?? "";
	if (["package-lock.json", "npm-shrinkwrap.json", "yarn.lock"].includes(basename))
		throw new Error(`${file} is forbidden; ${lane}-lock.yaml is the only lockfile`);
	const content = readFileSync(file);
	if (content.includes(0)) continue;
	const text = content.toString("utf8");
	if (forbiddenWord.test(text)) {
		throw new Error(`${file} still references the retired package manager or runtime`);
	}
	if (!isImageBuild(file) && forbiddenCommand.test(text)) {
		throw new Error(`${file} runs a command through ${lane} instead of vp`);
	}
}
console.log(
	`vite-plus ${vitePlus} on Node ${nodeVersion} with pnpm ${version}: pins, bundle, setup and command surface are consistent`,
);
