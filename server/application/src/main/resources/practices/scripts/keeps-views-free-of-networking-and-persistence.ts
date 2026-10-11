// Finds the I/O calls that the change adds inside SwiftUI view types: requests, decoding, files,
// preferences and Core Data. Each lead tells the type that holds the line. SwiftData's own view-side
// API (`@Query`, `modelContext.insert`) is not I/O in the view, so the script does not find it.
import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
import { sampleNote, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

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

export default definePracticeScript({
	meta: {
		kinds: {
			"urlsession-request": "An added line that uses URLSession or makes a URLRequest.",
			"json-coding": "An added line that makes a JSONDecoder or a JSONEncoder.",
			"core-data-fetch": "An added line that makes or runs an NSFetchRequest.",
			"user-defaults-access": "An added line that uses UserDefaults.",
			"file-manager-access": "An added line that uses FileManager.",
			"data-contents-of": "An added line that reads a file with Data(contentsOf:).",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.repo, ctx.change, {
			languages: ["swift"],
			patterns: IO_IN_VIEW,
			scope: SWIFTUI_VIEW,
			onlyInScope: true,
		});
		const leads: Lead[] = scan.hints.map((hint) => ({
			at: { change: hint.file, line: hint.line },
			kind: hint.pattern,
			facts: factsOf(hint.flags),
		}));
		const directions: string[] = [...sampleNote(scan)];
		if (scan.matched > 0) {
			directions.push(
				`${String(scan.matched)} candidate I/O calls were added inside or possibly inside view types. Read the enclosing type of each lead. A line in a store or model is not the view's I/O.`,
				"SwiftUI @AppStorage and @SceneStorage, Core Data @FetchRequest, and the supported SwiftData view-side APIs are not direct I/O.",
			);
		}
		if (scan.filesWithoutCheckout > 0) {
			directions.push(
				`${String(scan.filesWithoutCheckout)} Swift file(s) could not be read from the checkout; their leads carry enclosing=unknown and need the diff context to place.`,
			);
		}
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesAdded, scan.filesScanned));
		}
		return {
			leads,
			facts: {
				ioCallsInViews: scan.matched,
				viewLinesAdded: scan.linesInScope,
				swiftLinesAdded: scan.linesAdded,
				filesWithoutCheckout: scan.filesWithoutCheckout,
				filesScanned: scan.filesScanned,
				linesAdded: scan.linesAdded,
			},
			directions,
		};
	},
});
