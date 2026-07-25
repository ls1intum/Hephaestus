import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { CircleDollarSign } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminListLlmUsageOptions,
	adminListLlmUsageQueryKey,
	adminUpdateWorkspaceLlmBudgetMutation,
	getLlmUsageReportOptions,
} from "@/api/@tanstack/react-query.gen";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { AdminInstanceLlmUsageTable } from "@/components/admin/usage/AdminInstanceLlmUsageTable";
import { MonthNavigator } from "@/components/admin/usage/MonthNavigator";
import { SetBudgetDialog } from "@/components/admin/usage/SetBudgetDialog";
import { addMonths, currentMonthUtc } from "@/components/admin/usage/usageUtils";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/usage")({
	head: instanceAdminHead("AI usage"),
	component: AdminInstanceUsagePage,
});

function AdminInstanceUsagePage() {
	const queryClient = useQueryClient();
	const [month, setMonth] = useState(currentMonthUtc);
	const [editing, setEditing] = useState<AdminWorkspaceLlmUsage | null>(null);
	const [expanded, setExpanded] = useState<AdminWorkspaceLlmUsage | null>(null);

	const listQuery = useQuery({
		...adminListLlmUsageOptions({ query: { month } }),
		// Keep the previous month's rows on screen while stepping months — no spinner flash.
		placeholderData: keepPreviousData,
	});
	// Most expensive workspaces first — that's what an instance admin scans for. Name is a stable
	// tiebreak so the many $0.00 rows don't reshuffle on every refetch.
	const rows = [...(listQuery.data ?? [])].sort(
		(a, b) =>
			b.pricedTotalCostUsd - a.pricedTotalCostUsd || a.displayName.localeCompare(b.displayName),
	);
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
			queryClient.invalidateQueries({ queryKey: adminListLlmUsageQueryKey() });
			// The proxy caches its answer for about 30s, so "resumes now" would be a small lie. A bound
			// ("within a minute") rather than a hedge ("about a minute") — it is one the gate keeps.
			toast.success(
				variables.body.monthlyLlmBudgetUsd == null
					? "Cap removed. New calls resume within a minute."
					: "Cap saved. New calls resume within a minute.",
			);
			setEditing(null);
		},
		// No error toast: the dialog renders the server's reason as the amount field's error, where it
		// sits next to the value that was rejected instead of evaporating above it.
	});

	const handleSubmitBudget = (monthlyLlmBudgetUsd: number | null) => {
		if (!editing) {
			return;
		}
		updateBudget.mutate({
			path: { workspaceId: editing.workspaceId },
			// undefined (field omitted) clears the cap server-side.
			body: { monthlyLlmBudgetUsd: monthlyLlmBudgetUsd ?? undefined },
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
					canGoNext={month < currentMonthUtc()}
					onPrevMonth={() => setMonth((m) => addMonths(m, -1))}
					onNextMonth={() => setMonth((m) => (m < currentMonthUtc() ? addMonths(m, 1) : m))}
				/>
			</div>

			<AdminInstanceLlmUsageTable
				rows={rows}
				isCurrentMonth={month >= currentMonthUtc()}
				isLoading={listQuery.isLoading}
				error={listQuery.error}
				onRetry={() => listQuery.refetch()}
				expandedWorkspaceId={expanded?.workspaceId ?? null}
				detailReport={detailQuery.data}
				isDetailLoading={detailQuery.isLoading}
				detailError={detailQuery.error}
				onRetryDetail={() => detailQuery.refetch()}
				onToggleDetails={(workspace) =>
					setExpanded((current) =>
						current?.workspaceId === workspace.workspaceId ? null : workspace,
					)
				}
				onEditBudget={setEditing}
			/>

			<SetBudgetDialog
				workspace={editing}
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
