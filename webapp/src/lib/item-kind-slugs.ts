import type { KnownItemKind } from "@/lib/item-kinds";

/**
 * URL-facing spelling of a kind: the wire id carries a dot, which reads badly in a search
 * parameter, so the admin routes keep their own short slug and this map is where the two meet.
 * Kept free of the kind registry's icons so the browser extension links to the same routes from
 * the same map.
 */
const ITEM_KIND_SLUGS = {
	"scm.pull_request": "pull-request",
	"scm.pull_request_review": "pull-request-review",
	"scm.issue": "issue",
	"scm.repository": "repository",
	"scm.file": "file",
	"chat.thread": "conversation",
	"docs.document": "document",
} as const satisfies Record<KnownItemKind, string>;

export type ItemKindSlug = (typeof ITEM_KIND_SLUGS)[KnownItemKind];

function isSluggedKind(kind: string): kind is KnownItemKind {
	return Object.hasOwn(ITEM_KIND_SLUGS, kind);
}

export function itemKindSlug(kind: string): ItemKindSlug | undefined {
	return isSluggedKind(kind) ? ITEM_KIND_SLUGS[kind] : undefined;
}

export function itemKindFromSlug(slug: string): KnownItemKind | undefined {
	return Object.keys(ITEM_KIND_SLUGS)
		.filter(isSluggedKind)
		.find((kind) => ITEM_KIND_SLUGS[kind] === slug);
}
