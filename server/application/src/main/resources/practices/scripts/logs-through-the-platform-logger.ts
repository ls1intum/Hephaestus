// Finds the print-style calls and the logger calls that the change adds in application code, for
// each language. It also tells if a bounded search of the checkout finds a logger. The shapes of each
// language follow the criteria. The review decides from the file if a print is the output of the
// program, such as a CLI or a script, or a diagnostic that ships.
import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import { grep } from "../lib/grep.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
import { countLabel, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

interface Shapes {
	print: SourcePattern[];
	logger: SourcePattern[];
}

const SCRIPTING: Shapes = {
	print: [["console.*", /\bconsole\.(?:log|debug|info|warn|error)\s*\(/u]],
	logger: [["logger", /\b(?:log|logger)\.(?:trace|debug|info|warn|error|fatal)\s*\(/u]],
};

// The Apple system log: NSLog and NSLogv write to it, as the os_log family does.
const APPLE_SYSTEM_LOG: SourcePattern[] = [
	["os_log", /\bos_log(?:_(?:info|debug|error|fault))?\s*\(/u],
	["NSLog", /\bNSLogv?\s*\(/u],
];

// android.util.Log at any level and whatever its tag, and Timber over it. A bare `Log.` is read by name:
// the import that makes it android.util.Log is the review's to confirm.
const ANDROID_LOG: SourcePattern[] = [
	["android.util.Log", /\b(?:android\.util\.)?Log\.(?:v|d|i|w|e|wtf)\s*\(/u],
	["Timber", /\bTimber\.(?:v|d|i|w|e|wtf)\s*\(/u],
];

// language -> the print-style shapes and the logger shapes the criteria list for it.
const DIAGNOSTICS: Record<string, Shapes> = {
	swift: {
		print: [
			["print(", /(?:^|[^\w.])print\s*\(/u],
			["debugPrint(", /\bdebugPrint\s*\(/u],
			["dump(", /(?:^|[^\w.])dump\s*\(/u],
		],
		logger: [
			[
				"Logger",
				/\bLogger\s*\(|\b(?:logger|Logger\.shared)\.(?:trace|debug|info|notice|warning|error|critical|fault|log)\s*\(/u,
			],
			...APPLE_SYSTEM_LOG,
		],
	},
	"objective-c": {
		print: [["printf(", /\bprintf\s*\(/u]],
		logger: APPLE_SYSTEM_LOG,
	},
	kotlin: {
		print: [["println(", /(?:^|[^\w.])println?\s*\(/u]],
		logger: [...ANDROID_LOG, ["logger", /\blog(?:ger)?\.(?:trace|debug|info|warn|error)\s*\(/u]],
	},
	java: {
		print: [
			["System.out/err", /\bSystem\.(?:out|err)\.print/u],
			["printStackTrace()", /\.printStackTrace\s*\(/u],
		],
		logger: [
			...ANDROID_LOG,
			["logger", /\b(?:log|logger|LOG|LOGGER)\.(?:trace|debug|info|warn|error|severe|fine)\s*\(/u],
		],
	},
	typescript: SCRIPTING,
	javascript: SCRIPTING,
	python: {
		print: [["print(", /(?:^|[^\w.])print\s*\(/u]],
		logger: [
			[
				"logging",
				/\b(?:logging|logger|log)\.(?:debug|info|warning|error|exception|critical)\s*\(/u,
			],
		],
	},
};

/** Where a print is the program's output rather than a diagnostic: the criteria's exemptions, by path. */
const TOOL_PATH =
	/(?:^|\/)(?:scripts?|tools?|bin|cli|Scripts|fastlane|ci)\/|\.(?:sh|mjs|cjs)$|(?:^|\/)main\.(?:py|ts|js)$|Playground|\.playground\//u;

/** The rows that the script lists, across all languages. */
const LISTED = 40;

export default definePracticeScript({
	meta: {
		kinds: {
			"print-call": "An added print-style call, such as print, println, console.log or System.out.",
			"logger-call":
				"An added call to a logger, such as Logger, os_log, NSLog, android.util.Log or logging.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const leads: Lead[] = [];
		let prints = 0;
		let loggers = 0;
		let filesScanned = 0;
		let linesAdded = 0;
		for (const [language, shapes] of Object.entries(DIAGNOSTICS)) {
			const scan = await scanAddedLines(ctx.repo, ctx.change, {
				languages: [language],
				patterns: [...shapes.print, ...shapes.logger],
				maxHints: LISTED,
			});
			filesScanned += scan.filesScanned;
			linesAdded += scan.linesAdded;
			prints += shapes.print.reduce((sum, [label]) => sum + countLabel(scan, label), 0);
			loggers += shapes.logger.reduce((sum, [label]) => sum + countLabel(scan, label), 0);
			for (const hint of scan.hints) {
				const isPrint = shapes.print.some(([label]) => label === hint.pattern);
				leads.push({
					at: { change: hint.file, line: hint.line },
					kind: isPrint ? "print-call" : "logger-call",
					facts: factsOf({
						language,
						call: hint.pattern,
						...hint.flags,
						toolPath: TOOL_PATH.test(hint.file),
					}),
				});
			}
		}
		const listed = leads.slice(0, LISTED);
		const printsInToolPaths = listed.filter(
			(lead) => lead.kind === "print-call" && lead.facts?.toolPath === true,
		).length;
		// Whether a bounded lexical search of the checkout matches a logger definition, import or native call.
		const existing = await grep(
			String.raw`\bLogger\s*\(|\bos_log|\bNSLogv?\s*\(|import android\.util\.Log\b|\bTimber\b|LoggerFactory|import logging|from 'pino'|from "pino"|winston`,
			ctx.repo,
			{ maxResults: 5 },
		);
		const directions: string[] =
			prints + loggers > listed.length
				? [
						`${String(prints + loggers)} diagnostic line(s) added; ${String(listed.length)} are listed. The counts by kind cover every line; the tool-path count covers only the listed rows.`,
					]
				: [];
		if (prints > 0) {
			directions.push(
				`${String(prints)} print-style call(s) added (${String(printsInToolPaths)} listed under a tool or script path) against ${String(loggers)} logger call(s); ${existing.length > 0 ? `the checkout matches a logger (${existing[0]?.file ?? ""})` : "a bounded search of the checkout matched no logger definition, which does not show there is none"}.`,
				"Read each print's file to decide whether it is the program's output, a DEBUG-only block or a shipped diagnostic.",
			);
		} else if (loggers > 0) {
			directions.push(`${String(loggers)} logger call(s) added and no print-style call.`);
		}
		if (listed.length === 0) {
			directions.push(...noMatchDirections(linesAdded, filesScanned));
		}
		return {
			leads: listed,
			facts: {
				printsAdded: prints,
				loggerCallsAdded: loggers,
				printsInToolPaths,
				checkoutHasLogger: existing.length > 0 ? 1 : 0,
				filesScanned,
				linesAdded,
			},
			directions,
		};
	},
});
