import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Outlet } from "@tanstack/react-router";
import { toast } from "sonner";
import { listWorkspacesQueryKey, updateFeaturesMutation } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection",
)({
	component: PracticeDetectionLayout,
});

function PracticeDetectionLayout() {
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const {
		practicesEnabled,
		isLoading: featuresLoading,
		isError: featuresError,
	} = useWorkspaceFeatures();
	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// `practicesEnabled` defaults optimistically to true while loading and on error, so a query error
	// leaves it unconfirmed. When confirmed-off, show an inline empty state that enables it HERE — far
	// better than bouncing the admin to a different page with no explanation.
	const practicesConfirmedEnabled = !featuresLoading && !featuresError && practicesEnabled;
	if (workspaceSlug && !featuresLoading && !practicesConfirmedEnabled) {
		return <PracticeDetectionDisabled workspaceSlug={workspaceSlug} />;
	}

	// `!practicesEnabled` is unreachable once the confirmed-off guard above has run; this only
	// catches the still-loading and no-workspace cases.
	if (featuresLoading || !workspaceSlug) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	// Sub-navigation (Rubric / Review settings / Activity) lives in the sidebar's collapsible
	// "Practice detection" group, not in an in-page tab strip. Each child route owns its own header
	// and container, so this layout is just the feature gate plus an outlet.
	return <Outlet />;
}

/** Inline empty state for a workspace that hasn't turned practice detection on yet — enable it here. */
function PracticeDetectionDisabled({ workspaceSlug }: { workspaceSlug: string }) {
	const queryClient = useQueryClient();
	const enable = useMutation({
		...updateFeaturesMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() });
			toast.success("Practice detection enabled");
		},
		onError: (error) =>
			toast.error("Couldn't enable practice detection", {
				description: error instanceof Error ? error.message : undefined,
			}),
	});

	return (
		<div className="container mx-auto max-w-2xl py-6">
			<Card>
				<CardHeader>
					<CardTitle>Practice detection is off</CardTitle>
					<CardDescription>
						Turn it on to configure runtimes, triggers, the review policy, and the practice catalog
						for this workspace.
					</CardDescription>
				</CardHeader>
				<CardContent>
					<Button
						disabled={enable.isPending}
						onClick={() =>
							enable.mutate({ path: { workspaceSlug }, body: { practicesEnabled: true } })
						}
					>
						{enable.isPending ? "Enabling…" : "Enable practice detection"}
					</Button>
				</CardContent>
			</Card>
		</div>
	);
}
