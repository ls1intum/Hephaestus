import { defineRule } from "@oxlint/plugins";

/**
 * A play function already receives `canvas` — a Testing Library instance bound to the story's own
 * canvas — so `within(canvasElement)` rebuilds by hand the argument that was handed in. Two spellings
 * of one thing is the whole cost: a reader has to check which one a file uses before copying a line
 * out of it.
 *
 * Only re-deriving `canvas` is wrong; reading the element itself is not. `within(…)` over a subtree
 * or a portal, and `canvasElement.querySelector`/`.clientWidth`, all keep working — see the `valid`
 * cases in the adjacent test for the shapes this must not touch.
 */
export const noWithinCanvasElement = defineRule({
	meta: {
		type: "problem",
		docs: {
			description:
				"Use the `canvas` a play function is handed rather than re-deriving it from `canvasElement`.",
		},
		messages: {
			redundant:
				"The play function already gets `canvas` bound to this element, so `within(canvasElement)` re-derives an argument it was handed. Destructure `canvas` in the play signature and use it directly. `within(...)` stays right for scoping into a subtree — `within(canvas.getByRole('group', …))` — or into a portal off the canvas.",
		},
	},
	create(context) {
		return {
			CallExpression(node) {
				if (node.callee.type !== "Identifier" || node.callee.name !== "within") return;

				const [root] = node.arguments;
				if (root?.type !== "Identifier" || root.name !== "canvasElement") return;

				context.report({ node: root, messageId: "redundant" });
			},
		};
	},
});
