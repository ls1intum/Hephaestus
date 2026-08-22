#!/usr/bin/env node
/**
 * Components under `webapp/src/components/**` are presentational: they take data as props and never
 * fetch. Fetching lives in the route file (or a `src/hooks/use-*.ts` it calls), which passes plain
 * props down. A component that fetches cannot be rendered in Storybook without a network mock, and a
 * story that needs a network mock is a story that can break for reasons the component does not own.
 *
 * Story files carry the second half of the rule: no MSW. Storybook's autodocs page mounts every story
 * of a file into one document, and `msw-storybook-addon` installs handlers on a single global worker,
 * so the last story's handlers answer for the whole page — an error story silently breaks its
 * siblings' Docs page while every isolated story, and therefore every test and every snapshot, stays
 * green. R3 keeps that page correct for whatever MSW stories remain.
 *
 * Biome cannot carry this: its plugins register globally over `files.includes` while this rule is
 * path-scoped (routes and hooks *must* import the query layer), and the repo has already recorded
 * GritQL silently degrading a plugin to an advisory `info` that exits 0. Vitest cannot carry it
 * either — scanning the tree inside a worker starves the route tests sharing it. Same reasoning, and
 * the same vacuous-pass guard, as `check-story-prose.mjs`.
 *
 * The allowlist is shrink-only: an entry that scans clean fails the build, so it cannot go stale.
 */
import { readdir, readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/** Resolved from this file, so the script runs identically from the repo root and from `webapp/`. */
const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const COMPONENTS = "webapp/src/components";
const WEBAPP_SRC = "webapp/src";

/** Modules that reach the network. `@/api/types.gen` is pure types and stays allowed everywhere. */
const FETCHING_MODULES = [
	"@/api/@tanstack/react-query.gen",
	"@/api/sdk.gen",
	"@/api/client",
	"@/api/client.gen",
];
const QUERY_HOOKS = [
	"useQuery",
	"useQueries",
	"useMutation",
	"useInfiniteQuery",
	"useSuspenseQuery",
	"useSuspenseInfiniteQuery",
	"useQueryClient",
];
const MOCK_MODULES = ["msw", "msw-storybook-addon", "story-mock-server", "@/mocks/handlers"];

/**
 * Every violator as of the gate landing. Shrink only: refactor a file, delete its line. Nothing may
 * be added — a new entry means a new component fetches, which is the thing this script forbids.
 */
const ALLOWLIST = {
	fetching: [
		"webapp/src/components/achievements/AchievementsView.tsx",
		"webapp/src/components/admin/AdminAchievementsPage.tsx",
		"webapp/src/components/admin/AdminDangerZoneSettings.tsx",
		"webapp/src/components/admin/ai/WorkspaceLlmProviderPanel.tsx",
		"webapp/src/components/admin/audit/AuthAuditPanel.tsx",
		"webapp/src/components/admin/config-audit/ConfigAuditPanel.tsx",
		"webapp/src/components/admin/integrations/AdminSlackNotificationSettings.tsx",
		"webapp/src/components/admin/integrations/outline/AddCollectionDialog.tsx",
		"webapp/src/components/admin/integrations/slack-channels/ChannelHistorySheet.tsx",
		"webapp/src/components/auth/ImpersonationBanner.tsx",
		"webapp/src/components/auth/LandingSignInCTA.tsx",
		"webapp/src/components/auth/SignInButtons.tsx",
		"webapp/src/components/settings/DangerZoneSection.tsx",
		"webapp/src/components/settings/SessionsSection.tsx",
		"webapp/src/components/workspace/create-workspace/ConnectGitLabStep.tsx",
	],
	storyMocks: [
		"webapp/src/components/admin/AdminSettingsPage.stories.tsx",
		"webapp/src/components/admin/audit/AuthAuditPanel.stories.tsx",
		"webapp/src/components/admin/config-audit/ConfigAuditPanel.stories.tsx",
		"webapp/src/components/admin/integrations/outline/AddCollectionDialog.stories.tsx",
		"webapp/src/components/admin/integrations/slack-channels/ChannelHistorySheet.stories.tsx",
		"webapp/src/components/settings/SessionsSection.stories.tsx",
	],
};

/**
 * Import statements start a line; a commented-out or quoted one does not. Multi-line specifier lists
 * are the repo's formatting for long imports, hence the newline-tolerant clause.
 */
const IMPORT = /^import\s+(type\s+)?([\w*{},\s$]*?)\s*from\s*["']([^"']+)["']/gm;
const BARE_IMPORT = /^import\s+["']([^"']+)["']/gm;
/** Inside a story's own JSX a hook name in prose is documentation, not a call. */
const COMMENT_LINE = /^\s*(\/\/|\/\*|\*)/;

const listFiles = async (directory, suffixes) => {
	const entries = await readdir(join(REPO_ROOT, directory), {
		recursive: true,
		withFileTypes: true,
	});
	return entries
		.filter((entry) => entry.isFile() && suffixes.some((suffix) => entry.name.endsWith(suffix)))
		.map((entry) => join(entry.parentPath, entry.name).slice(`${REPO_ROOT}/`.length))
		.sort();
};

const readSource = async (path) => await readFile(join(REPO_ROOT, path), "utf8");

/** A statement is type-only when it says so, or when every specifier it names does. */
const isTypeOnly = (typeKeyword, clause) => {
	if (typeKeyword) return true;
	const named = clause.match(/\{([\s\S]*)\}/);
	if (!named || clause.replace(/\{[\s\S]*\}/, "").trim().length > 0) return false;
	return named[1]
		.split(",")
		.map((specifier) => specifier.trim())
		.filter(Boolean)
		.every((specifier) => specifier.startsWith("type "));
};

const runtimeImports = (source) => {
	const modules = [];
	for (const [, typeKeyword, clause, module] of source.matchAll(IMPORT)) {
		if (!isTypeOnly(typeKeyword, clause)) modules.push(module);
	}
	for (const [, module] of source.matchAll(BARE_IMPORT)) modules.push(module);
	return modules;
};

const importsAny = (source, modules) =>
	runtimeImports(source).filter((module) =>
		modules.some((candidate) => module === candidate || module.startsWith(`${candidate}/`)),
	);

const calledQueryHooks = (source) => {
	const called = new Set();
	for (const line of source.split("\n")) {
		if (COMMENT_LINE.test(line)) continue;
		for (const hook of QUERY_HOOKS) {
			if (new RegExp(`\\b${hook}\\s*\\(`).test(line)) called.add(hook);
		}
	}
	return [...called];
};

const fetchingFailures = [];
const docsFailures = [];
const stale = [];

/** R1 — components take data as props. */
const componentFiles = (await listFiles(COMPONENTS, [".ts", ".tsx"])).filter(
	(path) => !path.endsWith(".stories.tsx") && !/\.test\.tsx?$/.test(path),
);
if (componentFiles.length === 0) {
	console.error(`No component files found under ${COMPONENTS} — this check would pass unchecked.`);
	process.exit(1);
}
for (const path of componentFiles) {
	const source = await readSource(path);
	const reasons = [
		...importsAny(source, FETCHING_MODULES).map((module) => `imports ${module}`),
		...calledQueryHooks(source).map((hook) => `calls ${hook}()`),
	];
	const allowed = ALLOWLIST.fetching.includes(path);
	if (reasons.length > 0 && !allowed) fetchingFailures.push(`${path} — ${reasons.join(", ")}`);
	if (reasons.length === 0 && allowed) stale.push(`${path} (fetching)`);
}

/** R2 — a story of a presentational component needs no network, so it needs no mock. */
const componentStories =
	componentFiles.length > 0 ? await listFiles(COMPONENTS, [".stories.tsx"]) : [];
if (componentStories.length === 0) {
	console.error(`No story files found under ${COMPONENTS} — this check would pass unchecked.`);
	process.exit(1);
}
for (const path of componentStories) {
	const source = await readSource(path);
	const mocked =
		importsAny(source, MOCK_MODULES).length > 0 || source.includes("story-mock-server");
	const allowed = ALLOWLIST.storyMocks.includes(path);
	if (mocked && !allowed) fetchingFailures.push(`${path} — mocks the network in a story`);
	if (!mocked && allowed) stale.push(`${path} (storyMocks)`);
}

/**
 * R3 — one global MSW worker serves a Docs page, so inlined stories answer each other's requests.
 * An iframe per story restores the isolation; opting out of autodocs avoids the page entirely.
 */
const allStories = await listFiles(WEBAPP_SRC, [".stories.tsx"]);
if (allStories.length === 0) {
	console.error(`No story files found under ${WEBAPP_SRC} — this check would pass unchecked.`);
	process.exit(1);
}
for (const path of allStories) {
	const source = await readSource(path);
	if (!source.includes("msw")) continue;
	if (!source.includes(`tags: ["autodocs"]`)) continue;
	if (source.includes("inline: false")) continue;
	docsFailures.push(path);
}

for (const [message, entries] of [
	[
		"Components take their data as props; fetching belongs in the route file or a src/hooks module.",
		fetchingFailures,
	],
	[
		'A story file that mocks the network must set docs: { story: { inline: false } } on its meta, or drop tags: ["autodocs"].',
		docsFailures,
	],
	[
		"These are no longer violations. Delete them from ALLOWLIST in this script — it only shrinks.",
		stale,
	],
]) {
	if (entries.length === 0) continue;
	console.error(message);
	for (const entry of entries) console.error(`  ${entry}`);
	console.error("");
}
if (fetchingFailures.length > 0 || docsFailures.length > 0 || stale.length > 0) process.exit(1);

console.log(
	`check-presentational-components: ${componentFiles.length} components, ${allStories.length} story files, ` +
		`${ALLOWLIST.fetching.length + ALLOWLIST.storyMocks.length} allowlisted.`,
);
