import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { LandingContainer } from "./landing";

// This route will be a parent for all routes that require authentication
export const Route = createFileRoute("/_authenticated")({
	component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
	const { isAuthenticated, isLoading } = useAuth();

	// Show loading state if still initializing authentication
	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner size="lg" />
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
