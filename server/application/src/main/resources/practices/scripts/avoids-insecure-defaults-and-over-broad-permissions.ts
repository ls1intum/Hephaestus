// Finds the added code lines that match a pattern of an insecure default, an over-broad permission or
// an exposed secret, in all languages. Each lead is a candidate: the review decides if it is real and
// exploitable on a reachable path.
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";

const KINDS = {
	"credential-named-value-in-url-path-query":
		"An added line that puts a credential-named value into a URL path or query.",
	"keychain-write":
		"An added keychain write, whose accessibility class comes from kSecAttrAccessible.",
	"logs-credential-named-value-response-body":
		"An added log or print call whose arguments name a token, a password, a credential or a response body.",
	"insecure-transport-tls-off":
		"An added line that allows plain HTTP or turns off the verification of TLS certificates.",
	"over-broad-permission-wildcard":
		"An added line that grants world-writable file modes, permits all requests, or allows every origin.",
	"credential-named-literal": "An added line that assigns a literal value to a credential-named field.",
};
type InsecureKind = keyof typeof KINDS;

// Each pattern fires on an added line. They are tuned to the surface of authentication, transport and
// secrets that a login or API change touches, where a quick review most often misses an insecure default.
const PATTERNS: [InsecureKind, RegExp][] = [
	// A credential-named value interpolated into a URL path or query. Matches ${...} and #{...} (JS,
	// Ruby, Kotlin) and Swift's \(...) interpolation of a credential-named variable into a path.
	[
		"credential-named-value-in-url-path-query",
		/["'`](?:https?:\/\/|\/)[^"'`\n]*(?:\\\(|[$#]\{)[^"'`\n)}]*\b(?:token|secret|password|api[_-]?key|auth|session|refresh)\w*/iu,
	],
	// An omitted kSecAttrAccessible defaults to WhenUnlocked. Whether the effective class, the
	// synchronization and the backup migration fit the stored secret is the review's decision.
	["keychain-write", /\bSecItemAdd\b|kSecValueData\b/u],
	[
		"logs-credential-named-value-response-body",
		/\b(?:print|NSLog|console\.(?:log|debug|info)|System\.out\.print\w*|log\.(?:debug|info|warn))\s*\([^)]*\b(?:token|password|secret|credential|response|body|account)\b/iu,
	],
	[
		"insecure-transport-tls-off",
		/NSAllowsArbitraryLoads|allowsArbitraryLoads|rejectUnauthorized\s*:\s*false|InsecureSkipVerify\s*:\s*true|verify\s*=\s*False|\.insecure\(\)|http:\/\/(?!localhost|127\.0\.0\.1|0\.0\.0\.0)/u,
	],
	[
		"over-broad-permission-wildcard",
		/chmod\s+0?777|\b0o?777\b|permitAll\(\)|\.anyRequest\(\)\.permitAll|Access-Control-Allow-Origin["'\s:]*\*|allowedOrigins?["'\s:]*\*/u,
	],
	[
		"credential-named-literal",
		/\b(?:password|secret|api[_-]?key|token)\s*[:=]\s*["'`][A-Za-z0-9_\-/+]{8,}["'`]/iu,
	],
];

function isComment(t: string): boolean {
	return t.startsWith("//") || t.startsWith("#") || t.startsWith("*") || t.startsWith("/*");
}

/** The fact that counts the leads of a kind: `keychain-write` becomes `keychainWrite`. */
function countFact(kind: InsecureKind): string {
	return kind.replaceAll(/-(?<letter>[a-z])/gu, (_, letter: string) => letter.toUpperCase());
}

export default definePracticeScript({
	meta: { kinds: KINDS },
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const leads: Lead[] = [];
		const byKind: Record<string, number> = {};
		for (const [path, df] of ctx.change) {
			for (const [line, content] of df.addedLines) {
				if (isComment(content.trimStart())) {
					continue;
				}
				const match = PATTERNS.find(([, re]) => re.test(content));
				if (match !== undefined) {
					const [kind] = match;
					leads.push({ at: { change: path, line }, kind });
					byKind[countFact(kind)] = (byKind[countFact(kind)] ?? 0) + 1;
				}
			}
		}
		const directions =
			leads.length > 0
				? [
						`Found ${leads.length} insecure-default or secret-exposure candidate(s) on added lines. Investigate each on the reachable auth and transport surface.`,
						"For credential-named URL, literal and logging leads, establish the value’s role under class 5 of the standard before deciding whether it exposes credential material.",
						"A keychain write takes its accessibility from kSecAttrAccessible, WhenUnlocked when omitted, which is not a defect by itself.",
						"Disabled TLS, wildcard CORS and world-writable permissions are over-broad. Confirm whether each is real and exploitable before deciding.",
					]
				: [];
		return {
			leads: leads.slice(0, 40),
			facts: { insecureDefaultCandidates: leads.length, ...byKind },
			directions,
		};
	},
});
