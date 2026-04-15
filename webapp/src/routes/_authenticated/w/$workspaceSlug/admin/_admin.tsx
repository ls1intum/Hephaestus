import { createFileRoute, Navigate, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin")({
	component: AdminLayout,
});

function AdminLayout() {
	const { workspaceSlug, isAdmin, isLoading } = useWorkspaceAccess();

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	if (!isAdmin && workspaceSlug) {
		return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug }} replace />;
	}

	return <Outlet />;
}
