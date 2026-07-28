export function isMentorRoute(pathname: string) {
	return pathname === "/mentor" || /^\/w\/[^/]+\/mentor(?:\/|$)/.test(pathname);
}

export function isCopilotExcludedRoute(pathname: string) {
	return (
		isMentorRoute(pathname) ||
		/^\/(?:admin|settings|legal)(?:\/|$)/.test(pathname) ||
		/^\/w\/[^/]+\/admin(?:\/|$)/.test(pathname) ||
		pathname === "/imprint" ||
		pathname === "/privacy"
	);
}
