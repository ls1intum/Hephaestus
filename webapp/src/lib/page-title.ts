const APP_NAME = "Hephaestus";

/** `"AI usage · Instance admin · Hephaestus"` — page name first, because a narrow tab truncates from the end. */
function pageTitle(page: string, scope: string): string {
	return `${page} · ${scope} · ${APP_NAME}`;
}

export function instanceAdminHead(page: string) {
	return () => ({ meta: [{ title: pageTitle(page, "Instance admin") }] });
}

export function workspaceAdminHead(page: string) {
	return () => ({ meta: [{ title: pageTitle(page, "Admin") }] });
}
