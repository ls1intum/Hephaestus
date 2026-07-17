import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Settings2, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
import {
	adminGetInstanceSettingsOptions,
	adminGetInstanceSettingsQueryKey,
	adminUpdateSilentModeMutation,
} from "@/api/@tanstack/react-query.gen";
import { SilentModeCard } from "@/components/admin/instance/SilentModeCard";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/settings")({
	component: AdminSettingsPage,
});

/**
 * Instance settings (#1386) — seeded with the emergency silent-mode brake. The instance-level
 * rollout controls (channel force-offs) join this page with #1357.
 */
function AdminSettingsPage() {
	const queryClient = useQueryClient();
	const settingsQuery = useQuery(adminGetInstanceSettingsOptions());

	const silentModeMutation = useMutation({
		...adminUpdateSilentModeMutation(),
		onSuccess: (data) => {
			queryClient.setQueryData(adminGetInstanceSettingsQueryKey(), data);
			toast.success(data.silentModeEngaged ? "Silent mode engaged" : "Silent mode released");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not update silent mode")),
	});

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<Settings2 className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Instance settings</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Instance-wide operator controls. These apply across every workspace and override workspace
					settings while active.
				</p>
			</header>

			{settingsQuery.data ? (
				<SilentModeCard
					settings={settingsQuery.data}
					isPending={silentModeMutation.isPending}
					onEngage={(reason) => silentModeMutation.mutate({ body: { engaged: true, reason } })}
					onRelease={() => silentModeMutation.mutate({ body: { engaged: false } })}
				/>
			) : settingsQuery.isError ? (
				// This is the page an operator reaches to engage the brake during an incident — if the
				// state won't load, surface an error + retry, never a skeleton that hides the control.
				<Alert variant="destructive">
					<TriangleAlert aria-hidden />
					<AlertTitle>Couldn't load instance settings</AlertTitle>
					<AlertDescription>
						{problemDetailOf(settingsQuery.error, "The silent-mode control is unavailable.")}{" "}
						<Button
							variant="link"
							className="h-auto p-0"
							onClick={() => settingsQuery.refetch()}
							disabled={settingsQuery.isFetching}
						>
							Retry
						</Button>
					</AlertDescription>
				</Alert>
			) : (
				<Skeleton className="h-52 w-full rounded-xl" />
			)}
		</div>
	);
}
