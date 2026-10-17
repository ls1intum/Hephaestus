import { scm } from "../kits/scm/index.ts";
import { countLabel, scanOccurrences, type SourcePattern } from "../kits/scm/source-scan.ts";
import { DIRECTION_CHARS, factsOf, noMatchDirections, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

interface Shapes {
	print: SourcePattern[];
	logger: SourcePattern[];
}

const SCRIPTING: Shapes = {
	print: [["console.*", /\bconsole\.(?:log|debug|info|warn|error)\s*\(/u]],
	logger: [["logger", /\b(?:log|logger)\.(?:trace|debug|info|warn|error|fatal)\s*\(/u]],
};

const APPLE_SYSTEM_LOG: SourcePattern[] = [
	["os_log", /\bos_log(?:_(?:info|debug|error|fault))?\s*\(/u],
	["NSLog", /\bNSLogv?\s*\(/u],
];

// A bare Log name does not establish android.util.Log; the review must resolve its import.
const ANDROID_LOG: SourcePattern[] = [
	["android.util.Log", /\b(?:android\.util\.)?Log\.(?:v|d|i|w|e|wtf)\s*\(/u],
	["Timber", /\bTimber\.(?:v|d|i|w|e|wtf)\s*\(/u],
];

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

const TOOL_PATH =
	/(?:^|\/)(?:scripts?|tools?|bin|cli|Scripts|fastlane|ci)\/|\.(?:sh|mjs|cjs)$|(?:^|\/)main\.(?:py|ts|js)$|Playground|\.playground\//u;

const LISTED = 40;

export const LABELS = {
	"print-call": "A selected print-style call, such as print, println, console.log or System.out.",
	"logger-call":
		"A selected call to a logger, such as Logger, os_log, NSLog, android.util.Log or logging.",
};

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		if (domain.kind !== "scm.pull_request" && domain.kind !== "scm.file") {
			throw new Error("this practice requires SCM source files");
		}
		const tree = domain.files?.head ?? null;
		const leads: ReviewLead[] = [];
		let prints = 0;
		let loggers = 0;
		let filesScanned = 0;
		let linesSelected = 0;
		for (const [language, shapes] of Object.entries(DIAGNOSTICS)) {
			const scan = await scanOccurrences(ctx, domain.files, {
				languages: [language],
				patterns: [...shapes.print, ...shapes.logger],
				maxHints: LISTED,
			});
			filesScanned += scan.filesScanned;
			linesSelected += scan.linesSelected;
			prints += shapes.print.reduce((sum, [label]) => sum + countLabel(scan, label), 0);
			loggers += shapes.logger.reduce((sum, [label]) => sum + countLabel(scan, label), 0);
			for (const hint of scan.hints) {
				const isPrint = shapes.print.some(([label]) => label === hint.pattern);
				leads.push({
					at: { file: hint.file, line: hint.line, endLine: hint.endLine },
					label: isPrint ? LABELS["print-call"] : LABELS["logger-call"],
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
			(lead) => lead.label === LABELS["print-call"] && lead.facts?.toolPath === true,
		).length;
		const { matches: existing } =
			tree === null
				? { matches: [] }
				: await tree.grep(
						String.raw`\bLogger\s*\(|\bos_log|\bNSLogv?\s*\(|import android\.util\.Log\b|\bTimber\b|LoggerFactory|import logging|from 'pino'|from "pino"|winston`,
						{ maxResults: 5 },
					);
		const directions: string[] =
			prints + loggers > listed.length
				? [
						`${String(prints + loggers)} diagnostic line(s) selected; ${String(listed.length)} are listed. The counts by kind cover every line; the tool-path count covers only the listed rows.`,
					]
				: [];
		if (prints > 0) {
			directions.push(
				oneLine(
					`${String(prints)} print-style call(s) selected (${String(printsInToolPaths)} listed under a tool or script path) against ${String(loggers)} logger call(s); ${existing.length > 0 ? `the checkout matches a logger (${existing[0]?.file ?? ""})` : "a bounded search of the checkout matched no logger definition, which does not show there is none"}.`,
					DIRECTION_CHARS,
				),
				"Read each print's file to decide whether it is the program's output, a DEBUG-only block or a shipped diagnostic.",
			);
		} else if (loggers > 0) {
			directions.push(`${String(loggers)} logger call(s) selected and no print-style call.`);
		}
		if (listed.length === 0) {
			directions.push(...noMatchDirections(linesSelected, filesScanned));
		}
		return ctx.review({
			leads: listed,
			facts: {
				printsSelected: prints,
				loggerCallsSelected: loggers,
				printsInToolPaths,
				checkoutHasLogger: existing.length > 0 ? 1 : 0,
				filesScanned,
				linesSelected,
			},
			directions,
		});
	},
});
