import { createFileRoute, redirect } from "@tanstack/react-router";

/**
 * The landing page now lives at `/` (see `routes/index.tsx`). This route is kept only as a redirect
 * so existing links and bookmarks to `/landing` keep working.
 */
export const Route = createFileRoute("/landing")({
	beforeLoad: () => {
		throw redirect({ to: "/" });
	},
});
