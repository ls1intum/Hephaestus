import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	getLlmUsageReportOptions,
	getLlmUsageReportQueryKey,
	updateWorkspaceLlmBudgetMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminLlmUsagePage } from "@/components/admin/usage/AdminLlmUsagePage";
import { SetOwnProviderBudgetDialog } from "@/components/admin/usage/SetOwnProviderBudgetDialog";
import { addMonths, currentMonthUtc } from "@/components/admin/usage/usageUtils";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/usage")({
	head: workspaceAdminHead("AI usage"),
	component: AdminUsageContainer,
});

function AdminUsageContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const [month, setMonth] = useState(currentMonthUtc);
	const [isEditingOwnProviderCap, setIsEditingOwnProviderCap] = useState(false);

	const reportQueryOptions = getLlmUsageReportOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
		query: { month },
	});
	const {
		data: report,
		isLoading,
		error,
		refetch,
	} = useQuery({
		...reportQueryOptions,
		enabled: Boolean(workspaceSlug),
		// Keep the previous month's report on screen while stepping months — no spinner flash.
		placeholderData: keepPreviousData,
	});

	const updateOwnProviderCap = useMutation({
		...updateWorkspaceLlmBudgetMutation(),
		onSuccess: (_data, variables) => {
			// Omitting `query` invalidates every cached month for this workspace, not just the one
			// on screen — the cap is not month-scoped.
			queryClient.invalidateQueries({
				queryKey: getLlmUsageReportQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
			});
			// The proxy caches its answer for about 30s, so "resumes now" would be a small lie.
			toast.success(
				variables.body.monthlyBudgetUsd == null
					? "Cap removed. New calls resume within about a minute."
					: "Cap saved. New calls resume within about a minute.",
			);
			setIsEditingOwnProviderCap(false);
		},
		// No toast: the dialog renders the server's reason next to the amount that caused it.
	});

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// ISO yyyy-MM compares lexicographically, so this also guards against stepping past "now".
	const isCurrentMonth = month >= currentMonthUtc();

	return (
		<>
			<AdminLlmUsagePage
				month={month}
				isCurrentMonth={isCurrentMonth}
				workspaceSlug={workspaceSlug ?? ""}
				report={report}
				isLoading={isWorkspaceLoading || isLoading || !workspaceSlug}
				error={error}
				onRetry={() => refetch()}
				onPrevMonth={() => setMonth((m) => addMonths(m, -1))}
				onNextMonth={() => setMonth((m) => (m < currentMonthUtc() ? addMonths(m, 1) : m))}
				onEditOwnProviderCap={() => setIsEditingOwnProviderCap(true)}
			/>
			<SetOwnProviderBudgetDialog
				open={isEditingOwnProviderCap}
				currentCapUsd={report?.ownProviderMonthlyBudgetUsd ?? null}
				fx={report?.fx}
				isPending={updateOwnProviderCap.isPending}
				serverError={
					updateOwnProviderCap.error != null
						? problemDetailOf(updateOwnProviderCap.error, "Couldn't save the cap")
						: null
				}
				onOpenChange={(open) => {
					if (!open) {
						setIsEditingOwnProviderCap(false);
					}
				}}
				onSubmit={(monthlyBudgetUsd) =>
					updateOwnProviderCap.mutate({
						path: { workspaceSlug: workspaceSlug ?? "" },
						// undefined (field omitted) clears the cap server-side.
						body: { monthlyBudgetUsd: monthlyBudgetUsd ?? undefined },
					})
				}
			/>
		</>
	);
}
