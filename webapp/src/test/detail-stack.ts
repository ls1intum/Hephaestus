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
 * The Practice reviews list a link opens, such as `results`, and the filters it sets, read with the
 * router's own search parser.
 */
export function listOpenedBy(link: HTMLElement): {
	list: string | undefined;
	search: Record<string, unknown>;
} {
	const url = new URL(String(link.getAttribute("href")), window.location.origin);
	const list = /\/admin\/practices\/reviews\/(?<list>[a-z]+)$/u.exec(url.pathname)?.groups?.list;
	return { list, search: parseSearch(url.search) };
}
