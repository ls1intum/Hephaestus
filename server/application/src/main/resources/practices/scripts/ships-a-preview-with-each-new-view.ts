// Finds the view types that the change declares and the previews that it adds, in each Swift file.
// The review pairs them, and reads if the data of each preview is representative.
import { factsOf } from "../lib/bounded-text.ts";
import { isCommentLine } from "../lib/declarations.ts";
import { isTestPath, languageOf } from "../lib/languages.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";

const VIEW_DECLARATION =
	/^\s*(?:@\w+\s+)*(?:(?:public|private|fileprivate|internal)\s+)?struct\s+(?<name>[A-Za-z_]\w*)\s*(?:<[^>]*>)?\s*:\s*[^{]*\bView\b/u;
const PREVIEW = /^\s*#Preview\b|:\s*PreviewProvider\b/u;

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
			"new-view-type": "An added struct that conforms to View, with its name.",
			preview: "An added #Preview or PreviewProvider.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const leads: Lead[] = [];
		let newViews = 0;
		let previewsAdded = 0;
		let filesWithNewViewAndNoPreview = 0;
		for (const [path, df] of ctx.change) {
			if (languageOf(path) !== "swift" || isTestPath(path)) {
				continue;
			}
			let views = 0;
			let previews = 0;
			for (const [line, content] of df.addedLines) {
				if (isCommentLine(content, "swift")) {
					continue;
				}
				const view = VIEW_DECLARATION.exec(content);
				if (view) {
					views += 1;
					leads.push({
						at: { change: path, line },
						kind: "new-view-type",
						facts: factsOf({ name: view.groups?.name ?? "" }),
					});
				} else if (PREVIEW.test(content)) {
					previews += 1;
					leads.push({ at: { change: path, line }, kind: "preview" });
				}
			}
			newViews += views;
			previewsAdded += previews;
			if (views > 0 && previews === 0) {
				filesWithNewViewAndNoPreview += 1;
			}
		}
		const directions: string[] = [];
		if (newViews > 0) {
			directions.push(
				`${newViews} new view type(s) declared and ${previewsAdded} preview(s) added; ${filesWithNewViewAndNoPreview} file(s) declare a view and add no preview — a preview may live in another file of the change, so scan the whole diff before deciding.`,
				"These counts pair no preview with a view. Read the previews and view bodies to trace the presentation coverage described in the criteria, including nested views and content delegated by an effect-only owner.",
			);
		}
		return {
			leads: leads.slice(0, 40),
			facts: { newViews, previewsAdded, filesWithNewViewAndNoPreview },
			directions,
		};
	},
});
