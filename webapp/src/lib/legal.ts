export type LegalPageId = "imprint" | "privacy";
export type LegalLocale = "en" | "de";

export const LEGAL_LOCALES: readonly LegalLocale[] = ["en", "de"] as const;

export const LEGAL_PAGE_TITLES: Record<LegalPageId, Record<LegalLocale, string>> = {
	imprint: { en: "Imprint", de: "Impressum" },
	privacy: { en: "Privacy Statement", de: "Datenschutzerklärung" },
};

export interface ResolvedLegalContent {
	markdown: string;
	source: "override" | "profile" | "disclaimer";
	locale: LegalLocale;
	profile: string;
}

// Profile names are joined into a URL path on the webapp origin, so we accept
// only the character class we ship filesystem directories in. Anything else
// (whitespace, `..`, `/`, ...) falls through to the disclaimer, mirroring the
// "unset profile" case — the resolver never constructs an unexpected URL.
const PROFILE_PATTERN = /^[a-z0-9][a-z0-9_-]{0,31}$/;

export function isValidLegalProfile(profile: string): boolean {
	return PROFILE_PATTERN.test(profile);
}

// Restrict hyperlink protocols in operator-supplied markdown. `javascript:`,
// `data:`, `vbscript:`, unknown schemes are silently dropped at render time.
const SAFE_HREF_PATTERN = /^(?:https?:|mailto:|tel:|#|\/)/i;
const SAFE_IMG_SRC_PATTERN = /^(?:https?:|\/)/i;

export function isSafeLegalHref(href: unknown): href is string {
	return typeof href === "string" && SAFE_HREF_PATTERN.test(href);
}

export function isSafeLegalImageSrc(src: unknown): src is string {
	return typeof src === "string" && SAFE_IMG_SRC_PATTERN.test(src);
}

export function detectLegalLocale(nav?: Pick<Navigator, "language" | "languages">): LegalLocale {
	const langs: string[] = [];
	if (nav?.languages) langs.push(...nav.languages);
	if (nav?.language) langs.push(nav.language);
	for (const lang of langs) {
		const tag = lang.toLowerCase();
		if (tag === "de" || tag.startsWith("de-")) return "de";
		if (tag === "en" || tag.startsWith("en-")) return "en";
	}
	return "en";
}

interface Candidate {
	url: string;
	source: ResolvedLegalContent["source"];
}

function buildCandidates(page: LegalPageId, locale: LegalLocale, profile: string): Candidate[] {
	const candidates: Candidate[] = [
		{ url: `/legal-overrides/${page}.${locale}.md`, source: "override" },
		{ url: `/legal-overrides/${page}.en.md`, source: "override" },
	];
	if (profile) {
		candidates.push(
			{ url: `/legal/profiles/${profile}/${page}.${locale}.md`, source: "profile" },
			{ url: `/legal/profiles/${profile}/${page}.en.md`, source: "profile" },
		);
	}
	candidates.push(
		{ url: `/legal/_disclaimer/${page}.${locale}.md`, source: "disclaimer" },
		{ url: `/legal/_disclaimer/${page}.en.md`, source: "disclaimer" },
	);
	return candidates;
}

async function tryFetch(url: string, signal?: AbortSignal): Promise<string | null> {
	let response: Response;
	try {
		response = await fetch(url, { signal, cache: "no-cache" });
	} catch (err) {
		// Preserve abort semantics so the caller can distinguish teardown from
		// a network failure; everything else is "candidate missing, keep cascading".
		if (err instanceof DOMException && err.name === "AbortError") throw err;
		return null;
	}
	if (!response.ok) return null;
	// Defeat SPA fallbacks that served /index.html with a 200. nginx is configured
	// to 404 on missing legal files, but the client-side guard protects forks whose
	// reverse proxy is not (yet) configured that way.
	const contentType = response.headers.get("content-type") ?? "";
	if (contentType.includes("text/html")) return null;
	const body = await response.text();
	if (!body.trim()) return null;
	if (body.trimStart().toLowerCase().startsWith("<!doctype html")) return null;
	return body;
}

export async function resolveLegalContent(
	page: LegalPageId,
	locale: LegalLocale,
	options: { signal?: AbortSignal; profile?: string } = {},
): Promise<ResolvedLegalContent> {
	const raw = (options.profile ?? "").trim();
	const profile = isValidLegalProfile(raw) ? raw : "";
	const candidates = buildCandidates(page, locale, profile);
	for (const candidate of candidates) {
		const markdown = await tryFetch(candidate.url, options.signal);
		if (markdown !== null) {
			const matchedLocale: LegalLocale = candidate.url.endsWith(".de.md") ? "de" : "en";
			return { markdown, source: candidate.source, locale: matchedLocale, profile };
		}
	}
	// The disclaimer cascade terminates every call; reaching this line means the
	// shipped fallback is missing, which is a packaging bug, not a runtime state.
	throw new Error(`Unable to resolve legal content for page=${page} locale=${locale}`);
}
