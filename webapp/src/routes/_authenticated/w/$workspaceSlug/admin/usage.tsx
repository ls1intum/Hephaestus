import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { useRef, useState } from "react";
import { toast } from "sonner";
import {
	getLlmUsageReportOptions,
	getLlmUsageReportQueryKey,
	updateWorkspaceLlmBudgetMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminLlmUsagePage } from "@/components/admin/usage/AdminLlmUsagePage";
import { SetOwnProviderBudgetDialog } from "@/components/admin/usage/SetOwnProviderBudgetDialog";
import {
	monthOf,
	USAGE_SEARCH_PARAMS,
	usageSearchSchema,
} from "@/components/admin/usage/usage-search";
import {
	addMonths,
	canStepForwardFrom,
	isCurrentMonthUtc,
} from "@/components/admin/usage/usage-utils";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/usage")({
	head: workspaceAdminHead("AI usage"),
	component: AdminUsageContainer,
	validateSearch: usageSearchSchema,
	search: { middlewares: [retainSearchParams(USAGE_SEARCH_PARAMS)] },
});

function AdminUsageContainer() {
	const queryClient = useQueryClient();
	// The slug is validated by the admin layout's beforeLoad, so it is always present here.
	const { workspaceSlug } = Route.useParams();
	const month = monthOf(Route.useSearch());
	const navigate = useNavigate({ from: Route.fullPath });
	// Stepping a month is a navigation, so the month on screen is always the month in the address bar
	// and always forwardable. `replace` is deliberately not used: Back stepping months is the point.
	const goToMonth = (next: string) => navigate({ search: (prev) => ({ ...prev, month: next }) });
	const [isEditingOwnProviderCap, setIsEditingOwnProviderCap] = useState(false);
	// Whether the cap dialog is on screen *right now*. React Query snapshots a mutation's options when
	// `mutate` is called, so the state its callbacks close over is the state from that moment — the
	// one value that cannot answer "is that field still on screen to report into".
	const isEditingRef = useRef(false);
	const editOwnProviderCap = (isEditing: boolean) => {
		isEditingRef.current = isEditing;
		setIsEditingOwnProviderCap(isEditing);
	};

	const {
		data: report,
		isLoading,
		error,
		refetch,
	} = useQuery({
		...getLlmUsageReportOptions({ path: { workspaceSlug }, query: { month } }),
		// Keep the previous month's report on screen while stepping months — no spinner flash.
		placeholderData: keepPreviousData,
	});

	const updateOwnProviderCap = useMutation({
		...updateWorkspaceLlmBudgetMutation(),
		onSuccess: (_data, variables) => {
			// Omitting `query` invalidates every cached month for this workspace, not just the one
			// on screen — the cap is not month-scoped.
			queryClient.invalidateQueries({
				queryKey: getLlmUsageReportQueryKey({ path: { workspaceSlug } }),
			});
			// The proxy caches its answer for about 30s, so "resumes now" would be a small lie. A bound
			// the gate actually keeps ("within a minute"), never a hedge.
			toast.success(
				variables.body.monthlyBudgetUsd == null
					? "Cap removed. New calls resume within a minute."
					: "Cap saved. New calls resume within a minute.",
			);
			editOwnProviderCap(false);
		},
		onError: (error) => {
			// Inline while the dialog that would show it is open, out loud once it is gone. ADR 0027:
			// https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0027-dialog-lifetime-and-where-a-write-outcome-lands.md
			if (!isEditingRef.current) {
				toast.error("Couldn't save the cap", { description: problemDetailOf(error) });
			}
		},
	});

	// Asked apart, not derived from each other: they agree today only because `usageSearchSchema`
	// clamps `month` to this month, and that clamp is not this file's to keep.
	const isCurrentMonth = isCurrentMonthUtc(month);
	const canGoNext = canStepForwardFrom(month);

	return (
		<>
			<AdminLlmUsagePage
				month={month}
				isCurrentMonth={isCurrentMonth}
				canGoNext={canGoNext}
				workspaceSlug={workspaceSlug}
				report={report}
				isLoading={isLoading}
				error={error}
				onRetry={() => refetch()}
				onPrevMonth={() => goToMonth(addMonths(month, -1))}
				onNextMonth={() => {
					if (canGoNext) goToMonth(addMonths(month, 1));
				}}
				onEditOwnProviderCap={() => editOwnProviderCap(true)}
			/>
			<SetOwnProviderBudgetDialog
				open={isEditingOwnProviderCap}
				currentCapUsd={report?.ownProviderMonthlyBudgetUsd ?? null}
				fx={report?.fx}
				isCurrentMonth={isCurrentMonth}
				isPending={updateOwnProviderCap.isPending}
				serverError={
					updateOwnProviderCap.error != null
						? problemDetailOf(updateOwnProviderCap.error, "Couldn't save the cap")
						: null
				}
				onOpenChange={(open) => {
					if (!open) {
						editOwnProviderCap(false);
						// Otherwise reopening the dialog shows the rejection of an amount the admin has since
						// walked away from, against a field they have not typed in yet.
						updateOwnProviderCap.reset();
					}
				}}
				onSubmit={(monthlyBudgetUsd) =>
					updateOwnProviderCap.mutate({
						path: { workspaceSlug },
						// undefined (field omitted) clears the cap server-side.
						body: { monthlyBudgetUsd: monthlyBudgetUsd ?? undefined },
					})
				}
			/>
		</>
	);
}
