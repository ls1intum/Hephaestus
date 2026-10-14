/**
 * Addresses in the Hephaestus web app the extension links to. Every link is navigation only: the
 * page it opens has its own session, consent and confirmations, and opening it changes nothing.
 * Route shapes: `webapp/src/routes/_authenticated/w/$workspaceSlug/**`.
 */
export function workspaceBase(webAppOrigin: string, workspaceSlug: string): string {
	return `${webAppOrigin}/w/${encodeURIComponent(workspaceSlug)}`;
}

/**
 * The reader's feedback page for the item, and for an admin the reviews of this item with its detail
 * open. The item id is the server's; it is never built from a provider's own number.
 */
export function workLinks(
	webAppOrigin: string,
	workspaceSlug: string,
	item: { id: string },
	admin: boolean,
): { trace: string; reviewDetails?: string } {
	const base = workspaceBase(webAppOrigin, workspaceSlug);
	const trace = `${base}/feedback/${encodeURIComponent(item.id)}`;
	return admin
		? {
				trace,
				reviewDetails: `${base}/admin/practices/reviews/reviews?${new URLSearchParams({
					itemId: item.id,
					detail: `item:${item.id}`,
				})}`,
			}
		: { trace };
}
