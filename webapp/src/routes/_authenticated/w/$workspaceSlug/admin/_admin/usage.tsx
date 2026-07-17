import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { getLlmUsageReportOptions } from "@/api/@tanstack/react-query.gen";
import { AdminLlmUsagePage } from "@/components/admin/usage/AdminLlmUsagePage";
import { addMonths, currentMonthUtc } from "@/components/admin/usage/usageUtils";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/usage")({
	component: AdminUsageContainer,
});

function AdminUsageContainer() {
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const [month, setMonth] = useState(currentMonthUtc);

	const reportQueryOptions = getLlmUsageReportOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
		query: { month },
	});
	const {
		data: report,
		isLoading,
		isError,
	} = useQuery({
		...reportQueryOptions,
		enabled: Boolean(workspaceSlug),
		// Keep the previous month's report on screen while stepping months — no spinner flash.
		placeholderData: keepPreviousData,
	});

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// ISO yyyy-MM compares lexicographically, so this also guards against stepping past "now".
	const isCurrentMonth = month >= currentMonthUtc();

	return (
		<AdminLlmUsagePage
			month={month}
			isCurrentMonth={isCurrentMonth}
			report={report}
			isLoading={isWorkspaceLoading || isLoading || !workspaceSlug}
			isError={isError}
			onPrevMonth={() => setMonth((m) => addMonths(m, -1))}
			onNextMonth={() => setMonth((m) => (m < currentMonthUtc() ? addMonths(m, 1) : m))}
		/>
	);
}
