import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Settings2 } from "lucide-react";
import { toast } from "sonner";
import {
	adminGetInstanceSettingsOptions,
	adminGetInstanceSettingsQueryKey,
	adminUpdateSilentModeMutation,
} from "@/api/@tanstack/react-query.gen";
import { SilentModeCard } from "@/components/admin/instance/SilentModeCard";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Skeleton } from "@/components/ui/skeleton";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/settings")({
	head: instanceAdminHead("Instance settings"),
	component: AdminSettingsPage,
});

function AdminSettingsPage() {
	const queryClient = useQueryClient();
	const settingsQuery = useQuery(adminGetInstanceSettingsOptions());

	const silentModeMutation = useMutation({
		...adminUpdateSilentModeMutation(),
		onSuccess: (data) => {
			queryClient.setQueryData(adminGetInstanceSettingsQueryKey(), data);
			toast.success(
				data.silentModeEngaged
					? "Silent mode engaged — nothing will be posted until you release it"
					: "Silent mode released — feedback and Slack messages go out again",
			);
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not update silent mode")),
	});

	return (
		<PageLayout className="py-6">
			<PageHeader
				icon={<Settings2 />}
				title="Instance settings"
				description="Instance-wide operator controls. These apply across every workspace and override workspace settings while active."
			/>

			{settingsQuery.data ? (
				<SilentModeCard
					settings={settingsQuery.data}
					isPending={silentModeMutation.isPending}
					onEngage={(reason) => silentModeMutation.mutate({ body: { engaged: true, reason } })}
					onRelease={() => silentModeMutation.mutate({ body: { engaged: false } })}
				/>
			) : settingsQuery.isError ? (
				// Incident-time page: on load failure show an error, never a skeleton that hides the control.
				<QueryErrorAlert
					error={settingsQuery.error}
					title="Couldn't load instance settings"
					onRetry={() => settingsQuery.refetch()}
				/>
			) : (
				<Skeleton className="h-52 w-full rounded-xl" />
			)}
		</PageLayout>
	);
}
