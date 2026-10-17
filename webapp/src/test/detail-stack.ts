import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { parseSearch } from "@/lib/router-search";

export function levelsOpenedBy(link: HTMLElement): string[] {
	const search = parseSearch(
		new URL(String(link.getAttribute("href")), window.location.origin).search,
	);
	return stackInSearch(search.detail);
}

export function listOpenedBy(link: HTMLElement): {
	list: string | undefined;
	search: Record<string, unknown>;
} {
	const url = new URL(String(link.getAttribute("href")), window.location.origin);
	const list = /\/admin\/practices\/reviews\/(?<list>[a-z]+)$/u.exec(url.pathname)?.groups?.list;
	return { list, search: parseSearch(url.search) };
}
