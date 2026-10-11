// Finds the permission-related references and the authorization requests that the change adds in Swift
// files, and the usage-description keys in the change or in the first plist and project files of the
// checkout, each with its path. A reference names a type or call to inspect. It does not show that the
// change requests restricted access. A key in some file does not show that the target declares it, and
// a key that is not found does not show that the target does not declare it. The review reads the
// target, the actual API and the description.
import { readFile } from "node:fs/promises";
import path from "node:path";

import {
	DIRECTION_CHARS,
	factsOf,
	listWithin,
	noMatchDirections,
	oneLine,
} from "../lib/bounded-text.ts";
import { globFilesSync } from "../lib/files.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
import { countLabel, sampleNote, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

// reference kind -> [a permission-related type or call to inspect, usage keys that may declare its access].
// A match is a place to look, not access: a type named in a signature or conformance requests nothing,
// the system photo picker needs no library permission, and an image picker reaches the camera only when
// the camera is its source.
const PERMISSION_REFERENCES: readonly [string, RegExp, string[]][] = [
	[
		"Location",
		/\bCLLocationManager\b/u,
		["NSLocationWhenInUseUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription"],
	],
	[
		"Camera",
		/\bAVCaptureDevice\b|\bAVCaptureSession\b|\bsourceType\s*[:=]\s*\.camera\b|\.isSourceTypeAvailable\s*\(\s*\.camera\s*\)/u,
		["NSCameraUsageDescription"],
	],
	[
		"Microphone",
		/\bAVAudioRecorder\b|\bAVAudioSession\b.*record|\.record\(/u,
		["NSMicrophoneUsageDescription"],
	],
	[
		"PhotoLibrary",
		/\bPHPhotoLibrary\b|\bPHAsset\b/u,
		["NSPhotoLibraryUsageDescription", "NSPhotoLibraryAddUsageDescription"],
	],
	["Notifications", /\bUNUserNotificationCenter\b/u, []],
	[
		"Calendar",
		/\bEKEventStore\b/u,
		[
			"NSCalendarsUsageDescription",
			"NSCalendarsFullAccessUsageDescription",
			"NSCalendarsWriteOnlyAccessUsageDescription",
		],
	],
	["Contacts", /\bCNContactStore\b/u, ["NSContactsUsageDescription"]],
	[
		"HealthKit",
		/\bHKHealthStore\b/u,
		["NSHealthShareUsageDescription", "NSHealthUpdateUsageDescription"],
	],
	["Tracking", /\bATTrackingManager\b/u, ["NSUserTrackingUsageDescription"]],
	[
		"Bluetooth",
		/\bCBCentralManager\b|\bCBPeripheralManager\b/u,
		["NSBluetoothAlwaysUsageDescription"],
	],
	["Motion", /\bCMMotionActivityManager\b|\bCMPedometer\b/u, ["NSMotionUsageDescription"]],
	["Speech", /\bSFSpeechRecognizer\b/u, ["NSSpeechRecognitionUsageDescription"]],
	["LocalNetwork", /\bNWBrowser\b|\bNetService\b/u, ["NSLocalNetworkUsageDescription"]],
];

const REQUEST: readonly SourcePattern[] = [
	[
		"authorization request",
		/\brequest(?:WhenInUse|Always)?Authorization\b|\brequestAccess\b|\brequestTrackingAuthorization\b|\bPHPhotoLibrary\.requestAuthorization\b/u,
	],
	...PERMISSION_REFERENCES.map(([kind, re]): SourcePattern => [
		`permission-related reference ${kind}`,
		re,
	]),
];

/** The lead kind of a reference: `PhotoLibrary` becomes `permission-reference-photo-library`. */
function referenceKind(kind: string): string {
	return `permission-reference-${kind.replaceAll(/(?<=[a-z])(?=[A-Z])/gu, "-").toLowerCase()}`;
}

/** The lead kind of each pattern label of the scan. */
const KIND_OF_LABEL = new Map<string, string>([
	["authorization request", "authorization-request"],
	...PERMISSION_REFERENCES.map(([kind]): [string, string] => [
		`permission-related reference ${kind}`,
		referenceKind(kind),
	]),
]);

const KINDS: Record<string, string> = {
	"authorization-request": "An added call that requests an authorization from the user.",
	"usage-key-added": "An added line of a plist or project file that holds a usage-description key.",
	...Object.fromEntries(
		PERMISSION_REFERENCES.map(([kind]) => [
			referenceKind(kind),
			`An added reference to a ${kind} type or call, to inspect for the permission that it needs.`,
		]),
	),
};

const USAGE_KEY = /\b(?<key>NS\w+UsageDescription)\b/gu;

/**
 * The references that get a direction each. A section has 10 directions, and the other directions take
 * at most 4 of them.
 */
const REFERENCE_LINES = 5;

/** How many plist/project files of the checkout are read for keys. */
const PROJECT_FILES_READ = 20;

interface CheckoutKeys {
	keys: Map<string, string[]>;
	filesFound: number;
	filesRead: number;
	unreadable: number;
}

async function usageKeysInCheckout(repoPath: string): Promise<CheckoutKeys> {
	const keys = new Map<string, string[]>();
	let files: string[] = [];
	try {
		files = [
			...globFilesSync("**/Info.plist", repoPath),
			...globFilesSync("**/project.yml", repoPath),
			...globFilesSync("**/project.yaml", repoPath),
			...globFilesSync("**/*.xcodeproj/project.pbxproj", repoPath),
		].filter((f) => !/(?:^|\/)(?:Pods|\.build|DerivedData|Carthage)\//u.test(f));
	} catch {
		// no readable checkout: no file read, so no key located
	}
	let unreadable = 0;
	const read = files.slice(0, PROJECT_FILES_READ);
	for (const file of read) {
		let text: string;
		try {
			text = await readFile(path.join(repoPath, file), "utf8");
		} catch {
			unreadable += 1;
			continue;
		}
		for (const match of text.matchAll(USAGE_KEY)) {
			const key = match.groups?.key ?? "";
			keys.set(key, [...new Set([...(keys.get(key) ?? []), file])]);
		}
	}
	return { keys, filesFound: files.length, filesRead: read.length - unreadable, unreadable };
}

export default definePracticeScript({
	meta: { kinds: KINDS },
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.repo, ctx.change, {
			languages: ["swift"],
			patterns: REQUEST,
		});
		const leads: Lead[] = scan.hints.flatMap((hint) => {
			const kind = KIND_OF_LABEL.get(hint.pattern);
			return kind === undefined
				? []
				: [{ at: { change: hint.file, line: hint.line }, kind, facts: factsOf(hint.flags) }];
		});
		// Usage keys the change itself adds, in any file.
		const keysInChange = new Map<string, string>();
		for (const [file, df] of ctx.change) {
			if (!/Info\.plist$|project\.ya?ml$|project\.pbxproj$/u.test(file)) {
				continue;
			}
			for (const [line, content] of df.addedLines) {
				for (const match of content.matchAll(USAGE_KEY)) {
					const key = match.groups?.key ?? "";
					keysInChange.set(key, file);
					leads.push({ at: { change: file, line }, kind: "usage-key-added", facts: { key } });
				}
			}
		}
		const checkout = await usageKeysInCheckout(ctx.repo);
		const references = PERMISSION_REFERENCES.filter(([kind]) =>
			scan.counts.has(`permission-related reference ${kind}`),
		);
		const located = (keys: string[]) =>
			keys.flatMap((key) => {
				const inChange = keysInChange.get(key);
				const where = [
					...new Set([
						...(inChange === undefined ? [] : [inChange]),
						...(checkout.keys.get(key) ?? []),
					]),
				];
				return where.length === 0 ? [] : [`${key} (${where.join(", ")})`];
			});
		const directions: string[] = [...sampleNote(scan)];
		if (references.length > 0) {
			const read = `${String(checkout.filesRead)} of ${String(checkout.filesFound)} plist/project file(s) read${checkout.unreadable > 0 ? `, ${String(checkout.unreadable)} unreadable` : ""}`;
			const lines = references.map(([kind, , keys]) => {
				if (keys.length === 0) {
					return `${kind} (no usage key applies).`;
				}
				const found = located(keys);
				return oneLine(
					`${kind} — keys located: ${found.length > 0 ? found.join("; ") : "none"}.`,
					DIRECTION_CHARS,
				);
			});
			const others = references.slice(REFERENCE_LINES - 1).map(([kind]) => kind);
			directions.push(
				`Permission-related references to inspect, with the usage keys located. Searched the change and ${read}.`,
				...(lines.length > REFERENCE_LINES
					? [
							...lines.slice(0, REFERENCE_LINES - 1),
							`Also referenced: ${listWithin(others, DIRECTION_CHARS - 40)}.`,
						]
					: lines),
				"A key located in a file does not show the target declares it, and a key not located does not show it is missing: read the target's own Info.plist or build settings, and the key the actual API and deployment target require.",
			);
		}
		const requests = countLabel(scan, "authorization request");
		if (requests > 0) {
			directions.push(
				`${requests} authorization requests were added. For each request, read the enclosing flag and the call chain. Decide whether it runs when the feature is reached or earlier. A root view can be the feature that needs authorization.`,
			);
		}
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesAdded, scan.filesScanned));
		}
		return {
			leads: leads.slice(0, 40),
			facts: {
				permissionReferenceKinds: references.length,
				authorizationRequests: requests,
				usageKeysAdded: keysInChange.size,
				usageKeysLocatedInCheckout: checkout.keys.size,
				projectFilesRead: checkout.filesRead,
				filesScanned: scan.filesScanned,
				linesAdded: scan.linesAdded,
			},
			directions,
		};
	},
});
