import { createFileRoute, Outlet, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin",
)({
	component: AdminLayout,
});

function AdminLayout() {
	const { workspaceSlug, isAdmin, isLoading } = useWorkspaceAccess();
	const navigate = useNavigate();

	useEffect(() => {
		if (!isLoading && !isAdmin && workspaceSlug) {
			navigate({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug },
				replace: true,
			});
		}
	}, [isLoading, isAdmin, workspaceSlug, navigate]);

	if (isLoading || (!isAdmin && workspaceSlug)) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner size="lg" />
			</div>
		);
	}

	return <Outlet />;
}
