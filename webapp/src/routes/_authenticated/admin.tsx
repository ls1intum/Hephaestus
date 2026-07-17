import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { TriangleAlert } from "lucide-react";
import { adminGetInstanceSettingsOptions } from "@/api/@tanstack/react-query.gen";
import { SilentModeBanner } from "@/components/admin/instance/SilentModeBanner";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { isAppAdmin, resolveCurrentUser } from "@/integrations/auth/guard";

/**
 * Instance-admin (APP_ADMIN) layout route (ADR 0017 native auth). Guards the whole `/admin`
 * subtree in `beforeLoad` via `isAppAdmin` (`appRole === "APP_ADMIN"`), redirecting non-admins
 * before any admin UI renders. The client is not a security boundary — every `/admin` endpoint is
 * enforced server-side by `hasAuthority('app_admin')`; this guard only avoids a pointless flash.
 *
 * The layout also pins the silent-mode state banner above every admin page (#1386): an engaged
 * emergency brake must stay visible no matter which console page the operator is on.
 */
export const Route = createFileRoute("/_authenticated/admin")({
	beforeLoad: async ({ context }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (!isAppAdmin(user)) {
			throw redirect({ to: "/" });
		}
	},
	component: AdminLayout,
});

function AdminLayout() {
	const settingsQuery = useQuery(adminGetInstanceSettingsOptions());
	const topStrip = settingsQuery.data?.silentModeEngaged ? (
		<SilentModeBanner settings={settingsQuery.data} />
	) : settingsQuery.isError ? (
		// The banner exists to make an engaged brake impossible to miss; if we can't read the delivery
		// state, say so and offer a retry rather than silently showing nothing.
		<Alert variant="warning">
			<TriangleAlert aria-hidden />
			<AlertTitle>Couldn't load the instance delivery state</AlertTitle>
			<AlertDescription>
				Silent mode may be engaged.{" "}
				<Button variant="link" className="h-auto p-0" onClick={() => settingsQuery.refetch()}>
					Retry
				</Button>
			</AlertDescription>
		</Alert>
	) : null;
	return (
		<>
			{topStrip ? <div className="mx-auto w-full max-w-6xl pt-6">{topStrip}</div> : null}
			<Outlet />
		</>
	);
}
