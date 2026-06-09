import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Outlet, useMatchRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import { listWorkspacesQueryKey, updateFeaturesMutation } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
	const navigate = useNavigate();
	const matchRoute = useMatchRoute();

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

	if (featuresLoading || !practicesEnabled || !workspaceSlug) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	const isCatalog = Boolean(
		matchRoute({
			to: "/w/$workspaceSlug/admin/ai/practice-detection/catalog",
			fuzzy: true,
		}),
	);
	const activeTab = isCatalog ? "catalog" : "policy";

	return (
		<div className="container mx-auto max-w-3xl py-6 space-y-6">
			<div>
				<h1 className="text-3xl font-bold tracking-tight">Practice detection</h1>
				<p className="text-muted-foreground">
					Configure how automated practice reviews run and which practices are evaluated.
				</p>
			</div>

			<Tabs
				value={activeTab}
				onValueChange={(value) => {
					if (value === "catalog") {
						navigate({
							to: "/w/$workspaceSlug/admin/ai/practice-detection/catalog",
							params: { workspaceSlug },
						});
					} else {
						navigate({
							to: "/w/$workspaceSlug/admin/ai/practice-detection",
							params: { workspaceSlug },
						});
					}
				}}
			>
				<TabsList>
					<TabsTrigger value="policy">Policy</TabsTrigger>
					<TabsTrigger value="catalog">Catalog</TabsTrigger>
				</TabsList>
			</Tabs>

			<Outlet />
		</div>
	);
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
