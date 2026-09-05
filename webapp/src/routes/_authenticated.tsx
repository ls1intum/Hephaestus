import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { consentIsPending, resolveCurrentUser } from "@/integrations/auth/guard";

// This route will be a parent for all routes that require authentication
export const Route = createFileRoute("/_authenticated")({
	// Gate the protected subtree before render: resolve the session through the query client
	// so the first paint is correct, and redirect unauthenticated users to /login with the
	// current path preserved as returnTo. Reaching the component therefore implies an
	// authenticated session — the component only handles the brief auth-probe loading window.
	beforeLoad: async ({ context, location }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (!user) {
			throw redirect({
				to: "/login",
				search: { returnTo: location.href },
			});
		}
		// The mask keeps the address bar on the page the reader asked for, so the notice reads as an
		// interruption of that page rather than a trip to somewhere else.
		if (await consentIsPending(context.queryClient)) {
			throw redirect({
				to: "/consent",
				search: { returnTo: location.href },
				mask: { to: location.pathname, search: location.search },
			});
		}
	},
	pendingComponent: () => (
		<div className="flex items-center justify-center h-96">
			<Spinner className="size-8" />
		</div>
	),
	component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
	const { isLoading } = useAuth();

	// The beforeLoad guard already redirected unauthenticated users to /login, so here we only
	// cover the brief window where the in-app auth probe (GET /user via useAuth) is still settling.
	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	return <Outlet />;
}
