/**
 * Document titles for the router's `head` option (rendered by `<HeadContent />` in `__root.tsx`).
 *
 * The instance console and the per-workspace console deliberately use the same word for the same
 * kind of object — both have an "AI models", an "AI usage" and an "Audit log" page — so the scope
 * segment is the only thing that tells two open tabs apart. The page name comes first because a
 * narrow tab truncates from the end.
 */
const APP_NAME = "Hephaestus";

/** `"AI usage · Instance admin · Hephaestus"`; omit `scope` for non-scoped pages. */
export function pageTitle(page: string, scope?: string): string {
	return scope ? `${page} · ${scope} · ${APP_NAME}` : `${page} · ${APP_NAME}`;
}

/** `head` for a route under `/admin` (the instance console). */
export function instanceAdminHead(page: string) {
	return () => ({ meta: [{ title: pageTitle(page, "Instance admin") }] });
}

/** `head` for a route under `/w/$workspaceSlug/admin` (the per-workspace console). */
export function workspaceAdminHead(page: string) {
	return () => ({ meta: [{ title: pageTitle(page, "Admin") }] });
}
