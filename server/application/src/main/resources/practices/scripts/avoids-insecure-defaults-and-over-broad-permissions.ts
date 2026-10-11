// Precompute HINTS for avoids-insecure-defaults-and-over-broad-permissions: locate insecure-default /
// over-broad-permission / secret-exposure constructs ADDED in the diff, across languages. CANDIDATES only —
// the LLM confirms whether each is a real, exploitable insecure default on a reachable path. General by
// design: a pattern table keyed off the construct, applied to every added code line regardless of language.
import type { DiffFile, Hint, PullRequestMetadata } from "../lib/types.ts";

// [label, regex] — each fires on an ADDED line. Tuned to the auth/transport/secret surface a login or API
// change touches, where a "looks fine" review most often misses the real insecure default.
const PATTERNS: [string, RegExp][] = [
	// A credential-named value interpolated into a URL path or query. Matches
	// ${...}/#{...} (JS/Ruby/Kotlin) and Swift's \(...) interpolation of a credential-named variable into a path.
	[
		"credential-named value in URL path/query",
		/["'`](?:https?:\/\/|\/)[^"'`\n]*(?:\\\(|[$#]\{)[^"'`\n)}]*\b(?:token|secret|password|api[_-]?key|auth|session|refresh)\w*/iu,
	],
	// Keychain write — an omitted kSecAttrAccessible defaults to WhenUnlocked; whether the effective class,
	// synchronization and backup migration fit the stored secret is the review's.
	["keychain write", /\bSecItemAdd\b|kSecValueData\b/u],
	// Logging a credential-named value or response body to console/log.
	[
		"logs credential-named value / response body",
		/\b(?:print|NSLog|console\.(?:log|debug|info)|System\.out\.print\w*|log\.(?:debug|info|warn))\s*\([^)]*\b(?:token|password|secret|credential|response|body|account)\b/iu,
	],
	// Insecure transport / disabled TLS verification.
	[
		"insecure transport / TLS off",
		/NSAllowsArbitraryLoads|allowsArbitraryLoads|rejectUnauthorized\s*:\s*false|InsecureSkipVerify\s*:\s*true|verify\s*=\s*False|\.insecure\(\)|http:\/\/(?!localhost|127\.0\.0\.1|0\.0\.0\.0)/u,
	],
	// Over-broad permissions / wildcard access.
	[
		"over-broad permission / wildcard",
		/chmod\s+0?777|\b0o?777\b|permitAll\(\)|\.anyRequest\(\)\.permitAll|Access-Control-Allow-Origin["'\s:]*\*|allowedOrigins?["'\s:]*\*/u,
	],
	// A literal assigned to a credential-named field.
	[
		"credential-named literal",
		/\b(?:password|secret|api[_-]?key|token)\s*[:=]\s*["'`][A-Za-z0-9_\-/+]{8,}["'`]/iu,
	],
];

function isComment(t: string): boolean {
	return t.startsWith("//") || t.startsWith("#") || t.startsWith("*") || t.startsWith("/*");
}

export default function avoidsInsecureDefaultsAndOverBroadPermissions(
	_repo: string,
	diffFiles: Map<string, DiffFile>,
	_m: PullRequestMetadata,
) {
	const hints: Hint[] = [];
	const byKind: Record<string, number> = {};
	for (const [path, df] of diffFiles) {
		for (const [line, content] of df.addedLines) {
			const trimmed = content.trimStart();
			if (isComment(trimmed)) {
				continue;
			}
			for (const [label, re] of PATTERNS) {
				if (re.test(content)) {
					hints.push({
						file: path,
						line,
						pattern: label,
						context: content.trim().slice(0, 160),
						inDiff: true,
						flags: {},
					});
					byKind[label] = (byKind[label] ?? 0) + 1;
					break;
				}
			}
		}
	}
	const directions =
		hints.length > 0
			? [
					`Found ${hints.length} insecure-default / secret-exposure candidate(s) on added lines — investigate each on the reachable auth/transport surface: for credential-named URL, literal and logging leads, establish the value’s role under class 5 of the standard before deciding whether it exposes credential material; a keychain write takes its accessibility from kSecAttrAccessible, WhenUnlocked when omitted, which is not a defect by itself; disabled TLS / wildcard CORS / world-writable perms are over-broad. Confirm whether each is real and exploitable before deciding.`,
				]
			: [];
	return {
		hints: hints.slice(0, 40),
		metrics: { insecureDefaultCandidates: hints.length, ...byKind },
		directions,
	};
}
