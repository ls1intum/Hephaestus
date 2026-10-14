// Finds the I/O calls that the change adds inside SwiftUI view types: requests, decoding, files,
// preferences and Core Data. Each lead tells the type that holds the line. SwiftData's own view-side
// API (`@Query`, `modelContext.insert`) is not I/O in the view, so the script does not find it.
import { scm } from "../kits/scm/index.ts";
import { sampleNote, scanOccurrences, type SourcePattern } from "../kits/scm/source-scan.ts";
import { factsOf, noMatchDirections } from "../lib/core/bounded-text.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

/** A SwiftUI view: a type conforming to `View`, or an `App`/`Scene`, which also declare a body. */
const SWIFTUI_VIEW = /\b(?:View|App|Scene)\b/u;

const IO_IN_VIEW: readonly SourcePattern[] = [
	["urlsession-request", /\bURLSession\b|\bURLRequest\s*\(/u],
	["json-coding", /\bJSON(?:Decoder|Encoder)\s*\(/u],
	["core-data-fetch", /\bNSFetchRequest\b|\.fetch\s*\(\s*NSFetchRequest/u],
	["user-defaults-access", /\bUserDefaults\b/u],
	["file-manager-access", /\bFileManager\b/u],
	["data-contents-of", /\bData\(contentsOf:/u],
];

export const LABELS = {
	"urlsession-request": "A selected line that uses URLSession or makes a URLRequest.",
	"json-coding": "A selected line that makes a JSONDecoder or a JSONEncoder.",
	"core-data-fetch": "A selected line that makes or runs an NSFetchRequest.",
	"user-defaults-access": "A selected line that uses UserDefaults.",
	"file-manager-access": "A selected line that uses FileManager.",
	"data-contents-of": "A selected line that reads a file with Data(contentsOf:).",
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
			patterns: IO_IN_VIEW,
			scope: SWIFTUI_VIEW,
			onlyInScope: true,
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
		const directions: string[] = [...sampleNote(scan)];
		if (scan.matched > 0) {
			directions.push(
				`${String(scan.matched)} candidate I/O calls were selected inside or possibly inside view types. Read the enclosing type of each lead. A line in a store or model is not the view's I/O.`,
				"SwiftUI @AppStorage and @SceneStorage, Core Data @FetchRequest, and the supported SwiftData view-side APIs are not direct I/O.",
			);
		}
		if (scan.filesWithUnknownLocation > 0) {
			directions.push(
				`${String(scan.filesWithUnknownLocation)} Swift file(s) have declarations the script could not place; their leads carry enclosing=unknown. Inspect their full source to locate them.`,
			);
		}
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesSelected, scan.filesScanned));
		}
		return ctx.review({
			leads,
			facts: {
				ioCallsInViews: scan.matched,
				viewLinesSelected: scan.linesInScope,
				swiftLinesSelected: scan.linesSelected,
				filesWithUnknownLocation: scan.filesWithUnknownLocation,
				filesScanned: scan.filesScanned,
				linesSelected: scan.linesSelected,
			},
			directions,
		});
	},
});
