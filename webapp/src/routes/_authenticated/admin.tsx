import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { resolveCurrentUser } from "@/integrations/auth/guard";

/**
 * Super-admin (APP_ADMIN) layout route (ADR 0017 native auth).
 *
 * <p>Guards the whole {@code /admin} subtree in {@code beforeLoad} so the redirect happens
 * before any child route renders (no flash of admin UI). The check resolves the current
 * user through the same query the rest of the app reads ({@link resolveCurrentUser}), so it
 * is cached and consistent with {@code useAuth()}.
 *
 * <p>App-admin is indicated by either {@code appRole === "APP_ADMIN"} or the {@code admin}
 * role on the current user — we accept either so the guard tolerates server-side role
 * representation differences. Non-admins are redirected to the dashboard.
 */
export const Route = createFileRoute("/_authenticated/admin")({
	beforeLoad: async ({ context }) => {
		const user = await resolveCurrentUser(context.queryClient);
		const isAppAdmin = user?.appRole === "APP_ADMIN" || (user?.roles ?? []).includes("admin");
		if (!isAppAdmin) {
			throw redirect({ to: "/" });
		}
	},
	component: () => <Outlet />,
});
