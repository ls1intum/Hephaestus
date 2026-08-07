import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { TraceListPage } from "@/components/practice-trace/TraceListPage";
import { type TraceSearch, traceSearchSchema } from "@/components/practice-trace/trace-search";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/reviews/")({
	validateSearch: traceSearchSchema,
	component: ReviewActivityListRoute,
});

function ReviewActivityListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const updateSearch = (patch: Partial<TraceSearch>) =>
		navigate({
			search: (previous) => {
				const next = { ...previous, ...patch };
				return { ...next, page: next.page || undefined };
			},
			replace: true,
		});

	return (
		<TraceListPage workspaceSlug={workspaceSlug} search={search} onSearchChange={updateSearch} />
	);
}
