import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { isAppAdmin, resolveCurrentUser } from "@/integrations/auth/guard";

/**
 * Super-admin (APP_ADMIN) layout route (ADR 0017 native auth).
 *
 * Guards the whole `/admin` subtree in `beforeLoad` so the redirect happens
 * before any child route renders (no flash of admin UI). The check resolves the current
 * user through the same query the rest of the app reads (`resolveCurrentUser`), so it
 * is cached and consistent with `useAuth()`.
 *
 * App-admin is indicated by either `appRole === "APP_ADMIN"` or the `admin`
 * role on the current user — we accept either so the guard tolerates server-side role
 * representation differences. Non-admins are redirected to the dashboard.
 */
export const Route = createFileRoute("/_authenticated/admin")({
	beforeLoad: async ({ context }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (!isAppAdmin(user)) {
			throw redirect({ to: "/" });
		}
	},
	component: () => <Outlet />,
});
