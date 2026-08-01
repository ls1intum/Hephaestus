import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { LibraryBig, Plus } from "lucide-react";
import { toast } from "sonner";
import {
	adminGetCuratedAreaQueryKey,
	adminGetCuratedCatalogOptions,
	adminGetCuratedCatalogQueryKey,
	adminGetCuratedPracticeQueryKey,
	adminUpdateCuratedAreaStatusMutation,
	adminUpdateCuratedPracticeStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CuratedArea, CuratedPracticeSummary } from "@/api/types.gen";
import { CuratedCatalog } from "@/components/admin/curated-catalog/CuratedCatalog";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { buttonVariants } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/")({
	head: instanceAdminHead("Practice catalog"),
	component: AdminCuratedCatalogPage,
});

// Separate keys: an area and a practice may share a slug, and one must not show the other busy.
const PRACTICE_STATUS_KEY = ["adminWriteCuratedPracticeStatus"];
const AREA_STATUS_KEY = ["adminWriteCuratedAreaStatus"];

function AdminCuratedCatalogPage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const search = Route.useSearch();
	const queryClient = useQueryClient();
	const catalogQuery = useQuery({ ...adminGetCuratedCatalogOptions() });

	// The entry's own detail is cached too; leaving it stale hands the editor an old ETag and turns
	// the next save into a conflict the administrator did nothing to cause.
	const refreshCatalogAnd = (kind: "practice" | "area", slug: string) => {
		void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
		void queryClient.invalidateQueries({
			queryKey:
				kind === "practice"
					? adminGetCuratedPracticeQueryKey({ path: { slug } })
					: adminGetCuratedAreaQueryKey({ path: { slug } }),
		});
	};

	const onStatusSettled = (kind: "practice" | "area", slug: string, offered: boolean) => ({
		onSuccess: () => {
			refreshCatalogAnd(kind, slug);
			const noun = kind === "practice" ? "Practice" : "Area";
			toast.success(offered ? `${noun} offered again` : `${noun} retired`);
		},
		onError: (error: unknown) => {
			refreshCatalogAnd(kind, slug);
			toast.error(
				problemStatusOf(error) === 412
					? `Someone else changed this ${kind} first. Reload to see the current state.`
					: `Couldn't update the ${kind}`,
				{ description: problemDetailOf(error) },
			);
		},
	});

	const updatePracticeStatus = useMutation(
		filedUnder(PRACTICE_STATUS_KEY, adminUpdateCuratedPracticeStatusMutation()),
	);
	const updateAreaStatus = useMutation(
		filedUnder(AREA_STATUS_KEY, adminUpdateCuratedAreaStatusMutation()),
	);
	const pendingPracticeSlugs = usePendingMutationIds<{ path: { slug: string } }, string>(
		PRACTICE_STATUS_KEY,
		(variables) => variables.path.slug,
	);
	const pendingAreaSlugs = usePendingMutationIds<{ path: { slug: string } }, string>(
		AREA_STATUS_KEY,
		(variables) => variables.path.slug,
	);

	return (
		<PageLayout>
			<PageHeader
				icon={<LibraryBig />}
				title="Practice catalog"
				description="What every new workspace receives. Hephaestus keeps it current, and your edits stay yours."
				// In the header rather than the toolbar, so a failed load is not a dead end.
				actions={
					<div className="flex flex-wrap gap-2">
						<Link
							from={Route.fullPath}
							to="/admin/catalog/areas/new"
							search={(previous) => previous}
							className={buttonVariants({ variant: "outline" })}
						>
							<Plus className="mr-1.5 size-4" aria-hidden />
							Add area
						</Link>
						<Link
							from={Route.fullPath}
							to="/admin/catalog/practices/new"
							search={(previous) => previous}
							className={buttonVariants()}
						>
							<Plus className="mr-1.5 size-4" aria-hidden />
							Add practice
						</Link>
					</div>
				}
			/>

			{catalogQuery.isPending ? (
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			) : catalogQuery.isError ? (
				<QueryErrorAlert
					error={catalogQuery.error}
					title="Couldn't load the practice catalog"
					onRetry={() => catalogQuery.refetch()}
				/>
			) : (
				<CuratedCatalog
					areas={catalogQuery.data.areas}
					practices={catalogQuery.data.practices}
					summary={catalogQuery.data.summary}
					search={search}
					pendingPracticeSlugs={pendingPracticeSlugs}
					pendingAreaSlugs={pendingAreaSlugs}
					onSearchChange={(next) => navigate({ search: next, replace: true })}
					onPracticeStatusChange={(practice: CuratedPracticeSummary, offered) =>
						updatePracticeStatus.mutate(
							{
								path: { slug: practice.slug },
								headers: { "If-Match": `"${practice.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onStatusSettled("practice", practice.slug, offered),
						)
					}
					onAreaStatusChange={(area: CuratedArea, offered) =>
						updateAreaStatus.mutate(
							{
								path: { slug: area.slug },
								headers: { "If-Match": `"${area.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onStatusSettled("area", area.slug, offered),
						)
					}
				/>
			)}
		</PageLayout>
	);
}
