import { definePlugin } from "@oxlint/plugins";
import { noRedundantInTheDocument } from "./rules/no-redundant-in-the-document.ts";
import { noWithinCanvasElement } from "./rules/no-within-canvas-element.ts";
import { typedStoryMeta } from "./rules/typed-story-meta.ts";

export default definePlugin({
	meta: { name: "hephaestus" },
	rules: {
		"no-redundant-in-the-document": noRedundantInTheDocument,
		"no-within-canvas-element": noWithinCanvasElement,
		"typed-story-meta": typedStoryMeta,
	},
});
