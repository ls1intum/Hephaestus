import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { resolveCurrentUser } from "@/integrations/auth/guard";
import { LandingContainer } from "./landing";

// This route will be a parent for all routes that require authentication
export const Route = createFileRoute("/_authenticated")({
	// Gate the protected subtree before render: resolve the session through the query client
	// so the first paint is correct, and redirect unauthenticated users to /login with the
	// current path preserved as returnTo. The component below remains a defensive fallback.
	beforeLoad: async ({ context, location }) => {
		const user = await resolveCurrentUser(context.queryClient);
		if (!user) {
			throw redirect({
				to: "/login",
				search: { returnTo: location.href },
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
	const { isAuthenticated, isLoading } = useAuth();

	// Show loading state if still initializing authentication
	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	// Show landing page instead of login for unauthenticated users
	if (!isAuthenticated) {
		return <LandingContainer />;
	}

	// User is authenticated, render the child routes
	return <Outlet />;
}
