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
	const { workspaceSlug } = Route.useParams();
	const month = monthOf(Route.useSearch());
	const navigate = useNavigate({ from: Route.fullPath });
	// No `replace`: stepping months and walking back through them with Back is the point.
	const goToMonth = (next: string) => navigate({ search: (prev) => ({ ...prev, month: next }) });
	const [isEditingOwnProviderCap, setIsEditingOwnProviderCap] = useState(false);
	// React Query snapshots a mutation's options at `mutate` time, so the state its callbacks close
	// over is the one from that moment and cannot say whether the field is still on screen.
	const isCapDialogOnScreenRef = useRef(false);
	const editOwnProviderCap = (isEditing: boolean) => {
		isCapDialogOnScreenRef.current = isEditing;
		setIsEditingOwnProviderCap(isEditing);
	};

	const {
		data: report,
		isLoading,
		error,
		refetch,
	} = useQuery({
		...getLlmUsageReportOptions({ path: { workspaceSlug }, query: { month } }),
		placeholderData: keepPreviousData,
	});

	const updateOwnProviderCap = useMutation({
		...updateWorkspaceLlmBudgetMutation(),
		onSuccess: (_data, variables) => {
			// A prefix key, omitting `query`: the cap is not month-scoped, so every cached month has
			// to go, not just the one on screen.
			queryClient.invalidateQueries({
				queryKey: getLlmUsageReportQueryKey({ path: { workspaceSlug } }),
			});
			toast.success(
				variables.body.monthlyBudgetUsd == null
					? "Cap removed. New calls resume within a minute."
					: "Cap saved. New calls resume within a minute.",
			);
			editOwnProviderCap(false);
		},
		onError: (error) => {
			// Inline while the dialog that would show it is open, out loud once it is gone (ADR 0027).
			if (!isCapDialogOnScreenRef.current) {
				toast.error("Couldn't save the cap", { description: problemDetailOf(error) });
			}
		},
	});

	// Asked apart, not derived from each other: they agree only because `usageSearchSchema` clamps
	// `month` to this month, and that clamp is not this file's to keep.
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
						// Otherwise reopening shows the rejection of an amount that is no longer on screen.
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
