import { definePlugin } from "@oxlint/plugins";
import { noRedundantInTheDocument } from "./rules/no-redundant-in-the-document.ts";
import { noWithinCanvasElement } from "./rules/no-within-canvas-element.ts";
import { typedStoryMeta } from "./rules/typed-story-meta.ts";

/**
 * The house rules oxlint has no rule for. Each one guards a convention this repo already decided —
 * the rule text is where the decision is written down, so read it before switching one off.
 */
export default definePlugin({
	meta: { name: "hephaestus" },
	rules: {
		"no-redundant-in-the-document": noRedundantInTheDocument,
		"no-within-canvas-element": noWithinCanvasElement,
		"typed-story-meta": typedStoryMeta,
	},
});
