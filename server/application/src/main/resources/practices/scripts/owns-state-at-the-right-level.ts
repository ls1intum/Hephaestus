// Finds each state declaration and each state wiring that the change adds to Swift files, with the
// type that encloses it. The review applies the four rules of the practice to each lead.
import { scm } from "../kits/scm/index.ts";
import { sampleNote, scanOccurrences, type SourcePattern } from "../kits/scm/source-scan.ts";
import { factsOf, noMatchDirections } from "../lib/core/bounded-text.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

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
		/^\s*(?:private\s+|fileprivate\s+|internal\s+)?var\s+[A-Za-z_][A-Za-z0-9_]*\s*(?::\s*[^{=\n]+)?(?:=\s*[^{\n]+)?\s*$/mu,
	],
	["environment-injection", /\.environment(?:Object)?\s*\(/u],
];

export const LABELS = {
	"state-creating-an-object":
		"A selected @State or @StateObject declaration that creates an object.",
	"state-property": "A selected @State declaration.",
	"state-object": "A selected @StateObject declaration.",
	"observed-object": "A selected @ObservedObject declaration.",
	binding: "A selected @Binding declaration.",
	bindable: "A selected @Bindable declaration.",
	"environment-value": "A selected @Environment or @EnvironmentObject declaration.",
	"app-storage": "A selected @AppStorage declaration.",
	query: "A selected @Query declaration.",
	"observable-type": "A selected line with @Observable, usually on a model type.",
	"observable-object-type": "A selected line that names ObservableObject, usually on a model type.",
	"published-property": "A selected @Published property.",
	"mutable-stored-property": "A selected stored var without a property wrapper.",
	"environment-injection": "A selected .environment or .environmentObject modifier.",
};
const LABEL_OF = new Map<string, string>(Object.entries(LABELS));

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		if (domain.kind !== "scm.pull_request" && domain.kind !== "scm.file") {
			throw new Error("this practice requires SCM source files");
		}
		const scan = await scanOccurrences(ctx, domain.files, {
			languages: ["swift"],
			patterns: STATE_DECLARATIONS,
			scope: SWIFTUI_VIEW,
			maxHints: 60,
		});
		const leads: ReviewLead[] = scan.hints.flatMap((hint) => {
			const label = LABEL_OF.get(hint.pattern);
			return label === undefined
				? []
				: [
						{
							at: { file: hint.file, line: hint.line, endLine: hint.endLine },
							label,
							facts: factsOf(hint.flags),
						},
					];
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
			directions.push(...noMatchDirections(scan.linesSelected, scan.filesScanned));
		}
		if (scan.matched > 0) {
			directions.push(
				`${scan.matched} state declaration(s) or wiring line(s) selected; ${inViews.length} of the listed rows lie inside view types. Walk each against the four rules.`,
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
				stateDeclarationsSelected: scan.matched,
				stateDeclarationsInViews: inViews.length,
				objectsCreatedInViews,
				storedVarsInViews,
				filesWithUnknownLocation: scan.filesWithUnknownLocation,
				filesScanned: scan.filesScanned,
				linesSelected: scan.linesSelected,
			},
			directions,
		});
	},
});
