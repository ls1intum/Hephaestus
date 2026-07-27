import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { CircleDollarSign } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import {
	adminGetLlmUsageReportOptions,
	adminGetLlmUsageReportQueryKey,
	adminUpdateWorkspaceLlmBudgetMutation,
	getLlmUsageReportOptions,
	getLlmUsageReportQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { AdminInstanceLlmUsageTable } from "@/components/admin/usage/AdminInstanceLlmUsageTable";
import { MonthNavigator } from "@/components/admin/usage/MonthNavigator";
import { SetBudgetDialog } from "@/components/admin/usage/SetBudgetDialog";
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
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/usage")({
	head: instanceAdminHead("AI usage"),
	component: AdminInstanceUsagePage,
	validateSearch: usageSearchSchema,
	search: { middlewares: [retainSearchParams(USAGE_SEARCH_PARAMS)] },
});

function AdminInstanceUsagePage() {
	const queryClient = useQueryClient();
	const month = monthOf(Route.useSearch());
	const navigate = useNavigate({ from: Route.fullPath });
	// No `replace`: stepping months and walking back through them with Back is the point.
	const goToMonth = (next: string) => navigate({ search: (prev) => ({ ...prev, month: next }) });
	const [editing, setEditing] = useState<AdminWorkspaceLlmUsage | null>(null);
	// React Query snapshots a mutation's options at `mutate` time, so the `editing` its callbacks
	// close over is the one from that moment and cannot say whether the field is still on screen.
	const onScreenWorkspaceRef = useRef<AdminWorkspaceLlmUsage | null>(null);
	const editBudgetFor = (workspace: AdminWorkspaceLlmUsage | null) => {
		onScreenWorkspaceRef.current = workspace;
		setEditing(workspace);
	};
	const [expanded, setExpanded] = useState<AdminWorkspaceLlmUsage | null>(null);

	const listQuery = useQuery({
		...adminGetLlmUsageReportOptions({ query: { month } }),
		placeholderData: keepPreviousData,
	});
	// Name is a stable tiebreak, so the many $0.00 rows do not reshuffle on every refetch.
	const rows = [...(listQuery.data?.workspaces ?? [])].sort(
		(a, b) =>
			b.instanceTotalCostUsd - a.instanceTotalCostUsd || a.displayName.localeCompare(b.displayName),
	);
	const fx = listQuery.data?.fx;
	const detailQuery = useQuery({
		...getLlmUsageReportOptions({
			path: { workspaceSlug: expanded?.workspaceSlug ?? "" },
			query: { month },
		}),
		enabled: expanded != null,
	});

	const updateBudget = useMutation({
		...adminUpdateWorkspaceLlmBudgetMutation(),
		onSuccess: (_data, variables) => {
			// Both keys are prefixes, omitting `query`: a budget is not month-scoped, so every cached
			// month has to go.
			queryClient.invalidateQueries({ queryKey: adminGetLlmUsageReportQueryKey() });
			// The expanded Details panel reads the *workspace-scoped* report, a different key family
			// that the prefix above does not reach, and it stays mounted across the write.
			queryClient.invalidateQueries({
				queryKey: getLlmUsageReportQueryKey({
					path: { workspaceSlug: variables.path.workspaceSlug },
				}),
			});
			// The proxy caches its answer for about 30s, so "resumes now" would be a small lie.
			toast.success(
				variables.body.monthlyBudgetUsd == null
					? "Budget removed. New calls resume within a minute."
					: "Budget saved. New calls resume within a minute.",
			);
			editBudgetFor(null);
		},
		onError: (error, variables) => {
			// Inline while the dialog that would show it is open, out loud once it is gone (ADR 0027).
			if (onScreenWorkspaceRef.current?.workspaceSlug !== variables.path.workspaceSlug) {
				toast.error("Couldn't save the budget", { description: problemDetailOf(error) });
			}
		},
	});

	// Asked apart, not derived from each other: they agree only because `usageSearchSchema` clamps
	// `month` to this month, and that clamp is not this file's to keep.
	const canGoNext = canStepForwardFrom(month);
	const isCurrentMonth = isCurrentMonthUtc(month);

	const handleSubmitBudget = (monthlyBudgetUsd: number | null) => {
		if (!editing) {
			return;
		}
		updateBudget.mutate({
			path: { workspaceSlug: editing.workspaceSlug },
			// undefined (field omitted) clears the budget server-side.
			body: { monthlyBudgetUsd: monthlyBudgetUsd ?? undefined },
		});
	};

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<header className="space-y-1">
					<div className="flex items-center gap-2">
						<CircleDollarSign className="size-6 text-muted-foreground" aria-hidden />
						<h1 className="text-2xl font-semibold">AI usage</h1>
					</div>
				</header>
				<MonthNavigator
					month={month}
					canGoNext={canGoNext}
					onPrevMonth={() => goToMonth(addMonths(month, -1))}
					onNextMonth={() => {
						if (canGoNext) goToMonth(addMonths(month, 1));
					}}
				/>
			</div>

			<AdminInstanceLlmUsageTable
				rows={rows}
				month={month}
				fx={fx}
				isCurrentMonth={isCurrentMonth}
				isLoading={listQuery.isLoading}
				error={listQuery.error}
				onRetry={() => listQuery.refetch()}
				expandedWorkspaceSlug={expanded?.workspaceSlug ?? null}
				detailReport={detailQuery.data}
				isDetailLoading={detailQuery.isLoading}
				detailError={detailQuery.error}
				onRetryDetail={() => detailQuery.refetch()}
				onToggleDetails={(workspace) =>
					setExpanded((current) =>
						current?.workspaceSlug === workspace.workspaceSlug ? null : workspace,
					)
				}
				onEditBudget={editBudgetFor}
			/>

			<SetBudgetDialog
				workspace={editing}
				fx={fx}
				isCurrentMonth={isCurrentMonth}
				isPending={updateBudget.isPending}
				serverError={
					updateBudget.error != null
						? problemDetailOf(updateBudget.error, "Couldn't save the budget")
						: null
				}
				onOpenChange={(open) => {
					if (!open) {
						editBudgetFor(null);
						// Otherwise the next workspace's dialog opens showing this one's rejection.
						updateBudget.reset();
					}
				}}
				onSubmit={handleSubmitBudget}
			/>
		</div>
	);
}
