import { defineRule, type ESTree, type Options } from "@oxlint/plugins";
import { propertyName, type VisitedProperty } from "../property.ts";

/**
 * The same list `vitest/expect-expect` carries in `.oxlintrc.json`, widened to the `expect*` and
 * `assert*` prefixes so the shared helpers in `src/test/` — `expectSettledVisible`,
 * `expectNoPageOverflow`, `expectGenuinelyDisabled` — count as the assertions they are. A Testing
 * Library `getBy*` query counts too: it throws when it finds nothing.
 *
 * `*` matches within one path segment, `**` across them, so `**.getBy*` reaches both
 * `canvas.getByRole` and `within(row).getByRole`.
 */
const ASSERT_FUNCTION_NAMES = [
	"expect*",
	"**.expect*",
	"assert*",
	"**.assert*",
	"getBy*",
	"**.getBy*",
	"getAllBy*",
	"**.getAllBy*",
	"findBy*",
	"**.findBy*",
	"findAllBy*",
	"**.findAllBy*",
	// A play that runs another story's play inherits that story's assertions.
	"**.play",
];

const escaped = (literal: string) => literal.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);

const asRegExp = (glob: string) =>
	new RegExp(
		`^${glob
			.split("**")
			.map((crossing) => crossing.split("*").map(escaped).join("[^.]*"))
			.join(".*")}$`,
	);

const configuredNames = (options: Readonly<Options>) => {
	const [first] = options;
	if (typeof first !== "object" || first === null || Array.isArray(first)) return undefined;
	const names = first.assertFunctionNames;
	if (!Array.isArray(names)) return undefined;
	return names.filter((name) => typeof name === "string");
};

export const playMustAssert = defineRule({
	meta: {
		type: "problem",
		docs: {
			description: "A story's `play` function must end in an assertion.",
		},
		messages: {
			noAssertion:
				"This play function drives the component but never asserts on the result, so it passes as long as nothing throws — it proves the clicks landed, not that they did anything. Assert what the interaction was for: a `getBy*` query for the element it should have produced, or `expect` on the value it should have changed.",
		},
		schema: [
			{
				type: "object",
				properties: { assertFunctionNames: { type: "array", items: { type: "string" } } },
				additionalProperties: false,
			},
		],
	},
	create(context) {
		const patterns = (configuredNames(context.options) ?? ASSERT_FUNCTION_NAMES).map(asRegExp);
		const plays: { key: ESTree.Node; asserted: boolean }[] = [];

		/** A `play:` holding a function literal. `play: sharedPlay` is somebody else's body. */
		const playFunction = (node: VisitedProperty) =>
			propertyName(node) === "play" &&
			(node.value.type === "ArrowFunctionExpression" || node.value.type === "FunctionExpression");

		return {
			Property(node) {
				if (playFunction(node)) plays.push({ key: node.key, asserted: false });
			},
			"Property:exit"(node) {
				if (!playFunction(node)) return;
				const play = plays.pop();
				if (play && !play.asserted) context.report({ node: play.key, messageId: "noAssertion" });
			},
			CallExpression(node) {
				const play = plays.at(-1);
				if (!play || play.asserted) return;
				const callee = context.sourceCode.getText(node.callee);
				play.asserted = patterns.some((pattern) => pattern.test(callee));
			},
		};
	},
});
