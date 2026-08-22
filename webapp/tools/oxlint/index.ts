import { definePlugin } from "@oxlint/plugins";
import { noManualQueryKey } from "./rules/no-manual-query-key.ts";
import { noRedundantInTheDocument } from "./rules/no-redundant-in-the-document.ts";
import { noStoryA11yOverride } from "./rules/no-story-a11y-override.ts";
import { noWithinCanvasElement } from "./rules/no-within-canvas-element.ts";
import { playMustAssert } from "./rules/play-must-assert.ts";
import { typedStoryMeta } from "./rules/typed-story-meta.ts";

export default definePlugin({
	meta: { name: "hephaestus" },
	rules: {
		"no-manual-query-key": noManualQueryKey,
		"no-redundant-in-the-document": noRedundantInTheDocument,
		"no-story-a11y-override": noStoryA11yOverride,
		"no-within-canvas-element": noWithinCanvasElement,
		"play-must-assert": playMustAssert,
		"typed-story-meta": typedStoryMeta,
	},
});
