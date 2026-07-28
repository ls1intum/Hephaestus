import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { useUpdateWorkspaceFeatures } from "@/hooks/use-update-workspace-features";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/_enabled")({
	component: PracticesEnabledLayout,
});

function PracticesEnabledLayout() {
	const { workspaceSlug } = Route.useParams();
	const { practicesEnabled, isLoading, isError } = useWorkspaceFeatures(workspaceSlug);

	if (isLoading) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spinner className="size-8" />
			</div>
		);
	}
	if (!isError && !practicesEnabled) {
		return <PracticesDisabled workspaceSlug={workspaceSlug} />;
	}
	return <Outlet />;
}

function PracticesDisabled({ workspaceSlug }: { workspaceSlug: string }) {
	const enable = useUpdateWorkspaceFeatures(workspaceSlug, {
		success: "Practices enabled",
		error: "Couldn't enable practices",
	});

	return (
		<div className="mx-auto w-full max-w-2xl">
			<Card>
				<CardHeader>
					<CardTitle>Practices aren't on yet</CardTitle>
					<CardDescription>
						Turn them on to curate the practice catalog and configure future reviews. Historical
						reviews remain available from the sidebar.
					</CardDescription>
				</CardHeader>
				<CardContent>
					<Button
						disabled={enable.isPending}
						onClick={() =>
							enable.mutate({ path: { workspaceSlug }, body: { practicesEnabled: true } })
						}
					>
						{enable.isPending ? "Enabling…" : "Enable practices"}
					</Button>
				</CardContent>
			</Card>
		</div>
	);
}
