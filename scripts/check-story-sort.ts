/**
 * Storybook's sidebar is ordered by `storySort.order` in `.storybook/preview.tsx`. That list names
 * top-level title segments, and a segment it does not name sorts alphabetically *after* every one it
 * does — so a story tree does not fail to appear, it appears at the bottom, below the fold, looking
 * like it was put there on purpose. `integrations` was buried that way once already.
 *
 * The other direction costs just as much and is quieter still: an entry that matches no story is a
 * name that was renamed or deleted somewhere else, left behind here ordering nothing, and the next
 * person to read the list takes it for a tree they have not found yet.
 *
 * A script rather than a lint rule, because neither half is a property of a file. A rule sees one
 * story at a time and can never fail on an `order` entry going stale — there is no file to report it
 * against. Only something that reads the whole tree at once can compare the two sets.
 */
import { readdir, readFile } from "node:fs/promises";
import { join, resolve } from "node:path";

/** Resolved from this file, so the gate answers the same from the repo root or from `webapp/`. */
const REPO_ROOT = resolve(import.meta.dirname, "..");
const STORIES_ROOT = "webapp/src";
const PREVIEW = "webapp/.storybook/preview.tsx";

/**
 * `.storybook/main.ts` globs `../src/**` with no `titlePrefix`, so an untitled story is auto-titled
 * from its path relative to `src` — which makes its top-level segment the first directory under
 * `src`, the same string `order` has to name.
 */
const STORY_SUFFIX = ".stories.tsx";

/**
 * A `meta` title, at exactly one tab: that indentation is what makes it a property of the top-level
 * `const meta` object rather than of the mock data around it. `AboutPage.stories.tsx` holds a
 * `title: "Project lead"` one tab in — fixture data for the page it renders — which is why this is
 * read inside the `meta` block instead of anywhere in the file.
 */
const META_OPENS = /^const meta\b/;
const BLOCK_CLOSES = /^[)}]/;
const META_TITLE = /^\ttitle:\s*(.*)$/;
const STRING_TITLE = /^"([^"]*)",?$/;

/** The first segment of a title or a path — the only part `storySort.order` sorts on. */
const topLevel = (path: string): string => path.split("/")[0] ?? path;

/**
 * Every exit from here is a failure a person has to act on, so each leaves the same shape behind: the
 * sentence, and status 1. A thrown error would say the same thing under a stack trace nobody needs.
 */
function fail(message: string): never {
	console.error(message);
	process.exit(1);
}

/** The title a story declares, or `undefined` when it takes the one derived from its path. */
function declaredTitle(source: string, path: string): string | undefined {
	const lines = source.split("\n");
	const opens = lines.findIndex((line) => META_OPENS.test(line));
	if (opens === -1) return undefined;
	for (const line of lines.slice(opens + 1)) {
		if (BLOCK_CLOSES.test(line)) return undefined;
		const title = META_TITLE.exec(line)?.[1];
		if (title === undefined) continue;
		const literal = STRING_TITLE.exec(title)?.[1];
		if (literal === undefined) {
			// A title assembled at runtime is a title this gate cannot compare, and a gate that skipped
			// it would report `order` entries as stale that are not.
			fail(
				`${path}: \`title\` is \`${title}\`, which this check cannot read. Spell a story's title as a string literal.`,
			);
		}
		return literal;
	}
	return undefined;
}

/** The bracketed body of `storySort.order`, found by balancing brackets rather than by regex. */
function orderBody(source: string): string {
	const sort = source.indexOf("storySort");
	if (sort === -1) fail(`${PREVIEW}: no \`storySort\` — nothing orders the sidebar.`);
	const opens = source.indexOf("[", source.indexOf("order:", sort));
	if (opens === -1) fail(`${PREVIEW}: \`storySort\` declares no \`order\` array.`);
	let depth = 0;
	for (let index = opens; index < source.length; index++) {
		if (source[index] === "[") depth++;
		if (source[index] === "]") {
			depth--;
			if (depth === 0) return source.slice(opens + 1, index);
		}
	}
	return fail(`${PREVIEW}: \`storySort.order\` is never closed.`);
}

function declaredOrder(source: string): string[] {
	const body = orderBody(source);
	if (body.includes("[")) {
		// Storybook lets an entry be a nested array that orders that tree's own children. Nothing here
		// uses one, and reading a flat list out of a nested one would silently drop names.
		fail(
			`${PREVIEW}: \`storySort.order\` holds a nested array. This check models a flat list of top-level segments — teach it the nested shape before using one.`,
		);
	}
	return [...body.matchAll(/"([^"]*)"/g)].map(([, entry]) => entry ?? "");
}

const previewSource = await readFile(join(REPO_ROOT, PREVIEW), "utf8");
const order = declaredOrder(previewSource);
if (order.length === 0) {
	fail(`${PREVIEW}: \`storySort.order\` is empty — this check would pass without checking.`);
}

const entries = await readdir(join(REPO_ROOT, STORIES_ROOT), { recursive: true });
const files = entries.filter((entry) => entry.endsWith(STORY_SUFFIX)).toSorted();
if (files.length === 0) {
	fail(`No story files under ${STORIES_ROOT} — this check would pass without checking.`);
}

/** Every top-level segment the tree actually declares, and one story that declares it. */
const declared = new Map<string, string>();
for (const file of files) {
	const path = `${STORIES_ROOT}/${file}`;
	const title = declaredTitle(await readFile(join(REPO_ROOT, path), "utf8"), path);
	const segment = topLevel(title ?? file.replace(STORY_SUFFIX, ""));
	if (!declared.has(segment)) declared.set(segment, path);
}

const listed = new Set(order);
const unlisted = [...declared].filter(([segment]) => !listed.has(segment));
const stale = order.filter((entry) => !declared.has(entry));

if (unlisted.length > 0 || stale.length > 0) {
	console.error(`Story titles and \`storySort.order\` in ${PREVIEW} have drifted apart.\n`);
	if (unlisted.length > 0) {
		console.error(
			"Declared by a story, missing from `order` — these sort below everything listed:\n",
		);
		for (const [segment, path] of unlisted) console.error(`  ${segment}  (${path})`);
		console.error("");
	}
	if (stale.length > 0) {
		console.error(
			"Listed in `order`, declared by no story — delete the entry, or fix the title:\n",
		);
		for (const entry of stale) console.error(`  ${entry}`);
		console.error("");
	}
	console.error(
		"Add the segment to `order` where it belongs, or drop the entry that names nothing.",
	);
	process.exit(1);
}

console.log(
	`check-story-sort: ${files.length} story files, ${declared.size} top-level segments, each named once in storySort.order.`,
);
