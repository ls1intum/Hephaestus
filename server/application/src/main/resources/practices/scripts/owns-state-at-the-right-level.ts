// Finds each state declaration and each state wiring that the change adds to Swift files, with the
// type that encloses it. The review applies the four rules of the practice to each lead.
import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import {
	definePracticeScript,
	expectKind,
	requireChange,
	type ReviewLead,
} from "../lib/practice-script.ts";
import { sampleNote, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

const SWIFTUI_VIEW = /\b(?:View|App|Scene)\b/u;

// The first pattern that matches a line gives its kind.
const STATE_DECLARATIONS: readonly SourcePattern[] = [
	["state-creating-an-object", /@State(?:Object)?\b[^=]*=\s*[A-Z][A-Za-z0-9_]*\s*\(/u],
	["state-property", /@State\b/u],
	["state-object", /@StateObject\b/u],
	["observed-object", /@ObservedObject\b/u],
	["binding", /@Binding\b/u],
	["bindable", /@Bindable\b/u],
	["environment-value", /@Environment(?:Object)?\b/u],
	["app-storage", /@AppStorage\b/u],
	["query", /@Query\b/u],
	["observable-type", /@Observable\b/u],
	["observable-object-type", /\bObservableObject\b/u],
	["published-property", /@Published\b/u],
	[
		"mutable-stored-property",
		/^\s*(?:private\s+|fileprivate\s+|internal\s+)?var\s+[A-Za-z_][A-Za-z0-9_]*\s*(?::\s*[^{=\n]+)?(?:=\s*[^{\n]+)?\s*$/u,
	],
	["environment-injection", /\.environment(?:Object)?\s*\(/u],
];

export const LABELS = {
	"state-creating-an-object": "An added @State or @StateObject declaration that creates an object.",
	"state-property": "An added @State declaration.",
	"state-object": "An added @StateObject declaration.",
	"observed-object": "An added @ObservedObject declaration.",
	binding: "An added @Binding declaration.",
	bindable: "An added @Bindable declaration.",
	"environment-value": "An added @Environment or @EnvironmentObject declaration.",
	"app-storage": "An added @AppStorage declaration.",
	query: "An added @Query declaration.",
	"observable-type": "An added line with @Observable, usually on a model type.",
	"observable-object-type": "An added line that names ObservableObject, usually on a model type.",
	"published-property": "An added @Published property.",
	"mutable-stored-property": "An added stored var without a property wrapper.",
	"environment-injection": "An added .environment or .environmentObject modifier.",
};
const LABEL_OF = new Map<string, string>(Object.entries(LABELS));

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.files?.head ?? null, requireChange(ctx.files), {
			languages: ["swift"],
			patterns: STATE_DECLARATIONS,
			scope: SWIFTUI_VIEW,
			maxHints: 60,
		});
		const leads: ReviewLead[] = scan.hints.flatMap((hint) => {
			const label = LABEL_OF.get(hint.pattern);
			return label === undefined
				? []
				: [{ at: { change: hint.file, line: hint.line }, label, facts: factsOf(hint.flags) }];
		});
		const inViews = scan.hints.filter((hint) => hint.flags.inScope === true);
		const objectsCreatedInViews = inViews.filter(
			(hint) => hint.pattern === "state-creating-an-object" || hint.pattern === "state-object",
		).length;
		const storedVarsInViews = inViews.filter(
			(hint) => hint.pattern === "mutable-stored-property",
		).length;
		const directions: string[] = [...sampleNote(scan)];
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesAdded, scan.filesScanned));
		}
		if (scan.matched > 0) {
			directions.push(
				`${scan.matched} state declaration(s) or wiring line(s) added; ${inViews.length} of the listed rows lie inside view types. Walk each against the four rules.`,
			);
		}
		if (objectsCreatedInViews > 0) {
			directions.push(
				`${objectsCreatedInViews} object(s) created in a view's @State/@StateObject — check in the checkout whether a parent already owns an instance of the same type.`,
			);
		}
		if (storedVarsInViews > 0) {
			directions.push(
				`${storedVarsInViews} plain stored var(s) in a view type. Read whether the view mutates each one: the first rule covers a value that the view owns and mutates. A var that the view only receives from its parent is an input.`,
			);
		}
		return ctx.review({
			leads,
			facts: {
				stateDeclarationsAdded: scan.matched,
				stateDeclarationsInViews: inViews.length,
				objectsCreatedInViews,
				storedVarsInViews,
				filesWithoutCheckout: scan.filesWithoutCheckout,
				filesScanned: scan.filesScanned,
				linesAdded: scan.linesAdded,
			},
			directions,
		});
	},
});
