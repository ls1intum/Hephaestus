import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth";

export const Route = createFileRoute("/_authenticated/_admin")({
	component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
	const { hasRole, isLoading } = useAuth();

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner size="lg" />
			</div>
		);
	}

	if (hasRole("admin") === false) {
		redirect({ to: "/", throw: true });
	}

	return <Outlet />;
}
