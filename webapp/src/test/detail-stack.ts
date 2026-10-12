import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { parseSearch } from "@/lib/router-search";

/**
 * The detail stack a link opens, read back with the app's own search parser, so an assertion
 * names levels (`result:<id>`) rather than how the URL happens to encode them.
 */
export function levelsOpenedBy(link: HTMLElement): string[] {
	const search = parseSearch(
		new URL(String(link.getAttribute("href")), window.location.origin).search,
	);
	return stackInSearch(search.detail);
}

/**
 * The review page a link opens, and the record it focuses: `review:<id>` alone, or
 * `review:<id> focus:result:<id>`. Read from the path and the router's own search parser.
 */
export function reviewPageOpenedBy(link: HTMLElement): string | undefined {
	const url = new URL(String(link.getAttribute("href")), window.location.origin);
	const reviewId = /\/admin\/practices\/reviews\/review\/(?<id>[^/]+)$/u.exec(url.pathname)?.groups
		?.id;
	if (reviewId === undefined) {
		return undefined;
	}
	const search: Record<string, unknown> = parseSearch(url.search);
	return typeof search.focus === "string"
		? `review:${reviewId} focus:${search.focus}`
		: `review:${reviewId}`;
}

/**
 * The Practice reviews list a link opens — `runs`, `results`, `feedback` — and the filters it
 * sets, read with the router's own search parser.
 */
export function listOpenedBy(link: HTMLElement): {
	list: string | undefined;
	search: Record<string, unknown>;
} {
	const url = new URL(String(link.getAttribute("href")), window.location.origin);
	const list = /\/admin\/practices\/reviews\/(?<list>runs|results|feedback)$/u.exec(url.pathname)
		?.groups?.list;
	return { list, search: parseSearch(url.search) };
}
