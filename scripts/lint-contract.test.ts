import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { rmSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";
import { test } from "node:test";

interface Fixture {
	path: string;
	rule: string;
	source: string;
}

void test("vp lint preserves house rules and type-aware diagnostics", () => {
	const suffix = `${process.pid}-${Date.now()}`;
	const story = (name: string) =>
		`webapp/src/components/ui/LintContract-${suffix}-${name}.stories.tsx`;
	const preferPath = story("prefer-title");
	const fixtures: Fixture[] = [
		{
			path: `webapp/src/lint-contract-${suffix}-query.ts`,
			rule: "hephaestus/no-manual-query-key",
			source: 'const opts = { queryKey: ["manual"] }; void opts;',
		},
		{
			path: `webapp/src/lint-contract-nön-ascii-${suffix}.ts`,
			rule: "hephaestus/no-non-ascii-filename",
			source: "export const value = true;",
		},
		{
			path: `webapp/src/lint-contract-${suffix}-clock.tsx`,
			rule: "hephaestus/no-nondeterministic-render",
			source: "const timestamp = Date.now(); void timestamp;",
		},
		{
			path: `webapp/src/lint-contract-${suffix}-query.test.tsx`,
			rule: "hephaestus/no-redundant-in-the-document",
			source: 'test("query", () => expect(canvas.getByRole("button")).toBeInTheDocument());',
		},
		{
			path: story("a11y"),
			rule: "hephaestus/no-story-a11y-override",
			source: 'export const Bad: Story = { parameters: { a11y: { test: "off" } } };',
		},
		{
			path: story("within"),
			rule: "hephaestus/no-within-canvas-element",
			source:
				'export const Bad: Story = { play: ({ canvas, canvasElement }) => { within(canvasElement); canvas.getByRole("button"); } };',
		},
		{
			path: story("play"),
			rule: "hephaestus/play-must-assert",
			source: "export const Bad: Story = { play: ({ userEvent }) => userEvent.click(trigger) };",
		},
		{
			path: preferPath,
			rule: "hephaestus/prefer-auto-story-title",
			source: `const meta = { title: "components/ui/${basename(preferPath, ".stories.tsx")}", component: Button } satisfies Meta<typeof Button>; export default meta;`,
		},
		{
			path: `webapp/src/lint-contract-${suffix}-svg.tsx`,
			rule: "hephaestus/svg-needs-accessible-name",
			source: 'const icon = <svg><path d="M0 0" /></svg>; void icon;',
		},
		{
			path: story("meta"),
			rule: "hephaestus/typed-story-meta",
			source: "const meta = { component: Button } satisfies Meta; export default meta;",
		},
		{
			path: `webapp/src/lint-contract-${suffix}-unsafe.ts`,
			rule: "typescript/no-unsafe-assignment",
			source: 'const unsafe: string = JSON.parse("null"); void unsafe;',
		},
	];

	try {
		for (const fixture of fixtures)
			writeFileSync(fixture.path, `${fixture.source}\n`, { flag: "wx" });
		const paths = fixtures.map((fixture) => fixture.path.replace("webapp/", ""));
		const result = spawnSync(
			join(process.cwd(), "node_modules/.bin/vp"),
			["-C", "webapp", "lint", ...paths],
			{ encoding: "utf8" },
		);
		const diagnostics = `${result.stdout}${result.stderr}`;
		assert.notEqual(result.status, 0, diagnostics);
		for (const fixture of fixtures) {
			const path = fixture.path.replace("webapp/", "").replaceAll(".", "\\.");
			const rule = fixture.rule.replace("/", "(?:/|\\()");
			// The default reporter prints `path:line:col … rule`; under GitHub Actions vp switches to
			// workflow annotations, which put the rule in `title=` before the path. Accept either order.
			assert.match(diagnostics, new RegExp(`(?:${path}:[^\\n]*${rule}|${rule}[^\\n]*${path}:)`));
		}
	} finally {
		for (const fixture of fixtures) rmSync(fixture.path, { force: true });
	}
});
