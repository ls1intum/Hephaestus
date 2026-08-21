import { defineRule } from "@oxlint/plugins";

/**
 * Anchored, so the subject has to *be* the query: `getByRole(…)`, `canvas.getByText(…)`. A query with
 * something read off it — `getByRole(…).closest("tr")` — can be null, and asserting on that is real.
 */
const GET_BY_QUERY = /\bgetBy[A-Za-z]+$/;

const VACUOUS_MATCHERS = new Set(["toBeInTheDocument", "toBeTruthy", "toBeDefined"]);

/**
 * `getBy*` throws when it finds nothing, so it already is the assertion. Wrapping it in
 * `expect(...).toBeInTheDocument()` or `.toBeTruthy()` adds a matcher that can only ever run against
 * an element that exists and is therefore never null — it asserts nothing, and a file full of them
 * trains the reader to skim past the assertions that do.
 *
 * Narrow on purpose: every neighbouring shape whose subject can actually be null still asserts
 * something, and must keep working. The `valid` cases in the adjacent test are that list.
 */
export const noRedundantInTheDocument = defineRule({
	meta: {
		type: "problem",
		docs: {
			description: "Do not wrap a `getBy*` query in a matcher that can never fail.",
		},
		messages: {
			vacuous:
				"`getBy*` already throws when the element is missing, so this matcher asserts nothing. Drop the `expect(...)` wrapper and let the query stand as the assertion. `toBeVisible()` is a real strengthening, but it reads a just-opened Base UI overlay as invisible for one frame, so use `expectSettledVisible` from `@/test/overlay` when the target is inside an overlay.",
		},
	},
	create(context) {
		return {
			CallExpression(node) {
				const matcher = node.callee;
				if (matcher.type !== "MemberExpression" || matcher.computed) return;
				if (!VACUOUS_MATCHERS.has(matcher.property.name)) return;

				const expectCall = matcher.object;
				if (expectCall.type !== "CallExpression") return;
				if (expectCall.callee.type !== "Identifier" || expectCall.callee.name !== "expect") return;

				const [subject] = expectCall.arguments;
				if (subject?.type !== "CallExpression") return;
				if (!GET_BY_QUERY.test(context.sourceCode.getText(subject.callee))) return;

				context.report({ node: subject, messageId: "vacuous" });
			},
		};
	},
});
