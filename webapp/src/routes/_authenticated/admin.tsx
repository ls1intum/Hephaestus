import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { isAppAdmin, resolveCurrentUser } from "@/integrations/auth/guard";

/**
 * Instance-admin (APP_ADMIN) layout route (ADR 0017 native auth). Guards the whole `/admin`
 * subtree in `beforeLoad` via `isAppAdmin` (`appRole === "APP_ADMIN"`), redirecting non-admins
 * before any admin UI renders. The client is not a security boundary — every `/admin` endpoint is
 * enforced server-side by `hasAuthority('app_admin')`; this guard only avoids a pointless flash.
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
