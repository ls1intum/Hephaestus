import { defineRule } from "@oxlint/plugins";

/** Anchored, so `getByRole(…).closest("tr")` — which can be null — is not a match. */
const GET_BY_QUERY = /\bgetBy[A-Za-z]+$/;

const VACUOUS_MATCHERS = new Set(["toBeInTheDocument", "toBeTruthy", "toBeDefined"]);

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
