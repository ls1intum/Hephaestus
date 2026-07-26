import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { CircleDollarSign } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminGetLlmUsageReportOptions,
	adminGetLlmUsageReportQueryKey,
	adminUpdateWorkspaceLlmBudgetMutation,
	getLlmUsageReportOptions,
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
	// Stepping a month is a navigation, so the month on screen is always the month in the address bar
	// and always forwardable. `replace` is deliberately not used: Back stepping months is the point.
	const goToMonth = (next: string) => navigate({ search: (prev) => ({ ...prev, month: next }) });
	const [editing, setEditing] = useState<AdminWorkspaceLlmUsage | null>(null);
	const [expanded, setExpanded] = useState<AdminWorkspaceLlmUsage | null>(null);

	const listQuery = useQuery({
		...adminGetLlmUsageReportOptions({ query: { month } }),
		// Keep the previous month's rows on screen while stepping months — no spinner flash.
		placeholderData: keepPreviousData,
	});
	// Most expensive workspaces first — that's what an instance admin scans for. Name is a stable
	// tiebreak so the many $0.00 rows don't reshuffle on every refetch.
	const rows = [...(listQuery.data?.workspaces ?? [])].sort(
		(a, b) =>
			b.instanceTotalCostUsd - a.instanceTotalCostUsd || a.displayName.localeCompare(b.displayName),
	);
	// The rate belongs to the month, not to any row, so it is read off the envelope: a month with no
	// workspaces in it has no first row to take it from, and still has a rate.
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
			// Prefix key (no options) invalidates every cached month.
			queryClient.invalidateQueries({ queryKey: adminGetLlmUsageReportQueryKey() });
			// The proxy caches its answer for about 30s, so "resumes now" would be a small lie. A bound
			// ("within a minute") rather than a hedge ("about a minute") — it is one the gate keeps.
			toast.success(
				variables.body.monthlyBudgetUsd == null
					? "Budget removed. New calls resume within a minute."
					: "Budget saved. New calls resume within a minute.",
			);
			setEditing(null);
		},
		// No error toast: the dialog renders the server's reason as the amount field's error, where it
		// sits next to the value that was rejected instead of evaporating above it.
	});

	// Two questions, not one: whether the stepper may move forward, and whether this *is* the current
	// month — which is what the budget editors and the "at today's rate" hint turn on. A month later
	// than this one answers no to both, so neither may be the other's negation.
	const canGoNext = canStepForwardFrom(month);
	const isCurrentMonth = isCurrentMonthUtc(month);

	const handleSubmitBudget = (monthlyBudgetUsd: number | null) => {
		if (!editing) {
			return;
		}
		updateBudget.mutate({
			path: { workspaceSlug: editing.workspaceSlug },
			// undefined (field omitted) clears the cap server-side.
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
				onEditBudget={setEditing}
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
						setEditing(null);
						// Otherwise the next workspace's dialog opens showing this one's rejection.
						updateBudget.reset();
					}
				}}
				onSubmit={handleSubmitBudget}
			/>
		</div>
	);
}
