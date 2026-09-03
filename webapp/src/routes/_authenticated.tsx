import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

import { getConsentStatusOptions } from "@/api/@tanstack/react-query.gen";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { resolveCurrentUser } from "@/integrations/auth/guard";
import { WorkspaceRouteGuard } from "./-WorkspaceRouteGuard";

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
		const consent = await context.queryClient.query(getConsentStatusOptions({}));
		if (!consent.completed) {
			throw redirect({ to: "/consent", search: { returnTo: location.href } });
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
	const { isLoading: authLoading } = useAuth();

	// The beforeLoad guard already redirected unauthenticated users to /login, so here we only
	// cover the brief window where the in-app auth probe (GET /user via useAuth) is still settling.
	if (authLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	return (
		<WorkspaceRouteGuard>
			<Outlet />
		</WorkspaceRouteGuard>
	);
}
