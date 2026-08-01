import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { LibraryBig, Plus } from "lucide-react";
import { toast } from "sonner";
import {
	adminGetCuratedCatalogOptions,
	adminGetCuratedCatalogQueryKey,
	adminUpdateCuratedAreaStatusMutation,
	adminUpdateCuratedPracticeStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CuratedArea, CuratedPracticeSummary } from "@/api/types.gen";
import { CuratedCatalog } from "@/components/admin/curated-catalog/CuratedCatalog";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/")({
	head: instanceAdminHead("Practice catalog"),
	component: AdminCuratedCatalogPage,
});

const STATUS_MUTATION_KEY = ["adminWriteCuratedStatus"];

function AdminCuratedCatalogPage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const search = Route.useSearch();
	const queryClient = useQueryClient();
	const catalogQuery = useQuery({ ...adminGetCuratedCatalogOptions() });

	const onStatusSettled = (kind: "practice" | "area", offered: boolean) => ({
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success(offered ? `The ${kind} is offered again` : `The ${kind} is no longer offered`);
		},
		onError: (error: unknown) => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.error(
				problemStatusOf(error) === 412
					? `The ${kind} changed before its status could be updated`
					: `Couldn't update the ${kind}`,
				{ description: problemDetailOf(error) },
			);
		},
	});

	const updatePracticeStatus = useMutation(
		filedUnder(STATUS_MUTATION_KEY, adminUpdateCuratedPracticeStatusMutation()),
	);
	const updateAreaStatus = useMutation(
		filedUnder(STATUS_MUTATION_KEY, adminUpdateCuratedAreaStatusMutation()),
	);
	const pendingSlugs = usePendingMutationIds<{ path: { slug: string } }, string>(
		STATUS_MUTATION_KEY,
		(variables) => variables.path.slug,
	);

	return (
		<PageLayout>
			<PageHeader
				icon={<LibraryBig />}
				title="Practice catalog"
				description="What every new workspace on this instance receives. Hephaestus keeps it current; your edits stay yours."
				actions={
					<div className="flex flex-wrap gap-2">
						<Button
							variant="outline"
							nativeButton={false}
							render={
								<Link
									from={Route.fullPath}
									to="/admin/catalog/areas/new"
									search={(previous) => previous}
								/>
							}
						>
							<Plus className="size-4" aria-hidden />
							Add area
						</Button>
						<Button
							nativeButton={false}
							render={
								<Link
									from={Route.fullPath}
									to="/admin/catalog/practices/new"
									search={(previous) => previous}
								/>
							}
						>
							<Plus className="size-4" aria-hidden />
							Add practice
						</Button>
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
					pendingSlugs={pendingSlugs}
					practicesInArea={(areaSlug) =>
						catalogQuery.data.practices
							.filter((practice) => practice.areaSlug === areaSlug && practice.status.offered)
							.map((practice) => practice.slug)
					}
					onSearchChange={(next) => navigate({ search: next, replace: true })}
					onPracticeStatusChange={(practice: CuratedPracticeSummary, offered) =>
						updatePracticeStatus.mutate(
							{
								path: { slug: practice.slug },
								headers: { "If-Match": `"${practice.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onStatusSettled("practice", offered),
						)
					}
					onAreaStatusChange={(area: CuratedArea, offered) =>
						updateAreaStatus.mutate(
							{
								path: { slug: area.slug },
								headers: { "If-Match": `"${area.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onStatusSettled("area", offered),
						)
					}
				/>
			)}
		</PageLayout>
	);
}
