import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { LibraryBig, Plus } from "lucide-react";
import { toast } from "sonner";
import {
	adminGetCuratedAreaQueryKey,
	adminGetCuratedCatalogOptions,
	adminGetCuratedCatalogQueryKey,
	adminGetCuratedPracticeQueryKey,
	adminPlaceCuratedPracticeMutation,
	adminReorderCuratedAreasMutation,
	adminReorderCuratedPracticesMutation,
	adminResetCuratedCatalogOrderMutation,
	adminUpdateCuratedAreaStatusMutation,
	adminUpdateCuratedPracticeStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CuratedCatalog as Catalog,
	CuratedArea,
	CuratedPractice,
	CuratedPracticeSummary,
} from "@/api/types.gen";
import { CuratedCatalog } from "@/components/admin/curated-catalog/CuratedCatalog";
import {
	orderedPracticeSlugs,
	placeCuratedPractice,
	reorderCuratedAreas,
	reorderCuratedPractices,
} from "@/components/admin/curated-catalog/curated-catalog-cache";
import { PracticeTreeSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Button, buttonVariants } from "@/components/ui/button";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/")({
	head: instanceAdminHead("Practice catalog"),
	component: AdminCuratedCatalogPage,
});

const PRACTICE_STATUS_KEY = ["adminWriteCuratedPracticeStatus"];
const AREA_STATUS_KEY = ["adminWriteCuratedAreaStatus"];
const STRUCTURE_SCOPE = { id: "admin-curated-catalog-structure" };

function AdminCuratedCatalogPage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const search = Route.useSearch();
	const queryClient = useQueryClient();
	const catalogQuery = useQuery({ ...adminGetCuratedCatalogOptions() });

	const detailKey = (kind: "practice" | "area", slug: string) =>
		kind === "practice"
			? adminGetCuratedPracticeQueryKey({ path: { slug } })
			: adminGetCuratedAreaQueryKey({ path: { slug } });
	const invalidateCatalog = () => {
		void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
	};

	const onPracticeStatusSettled = (slug: string, offered: boolean, successMessage?: string) => ({
		onSuccess: (updated: CuratedPractice) => {
			queryClient.setQueryData(detailKey("practice", slug), updated);
			invalidateCatalog();
			toast.success(
				successMessage ??
					(offered ? "Practice offered to workspaces" : "Practice is no longer offered"),
			);
		},
		onError: (error: unknown) => {
			queryClient.removeQueries({ queryKey: detailKey("practice", slug), exact: true });
			invalidateCatalog();
			toast.error(
				problemStatusOf(error) === 412
					? "The catalog changed before this action was saved. We reloaded the practice."
					: "Couldn't update the practice",
				{ description: problemDetailOf(error) },
			);
		},
	});
	const onAreaStatusSettled = (slug: string, offered: boolean) => ({
		onSuccess: (catalog: Catalog) => {
			queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), catalog);
			queryClient.removeQueries({ queryKey: detailKey("area", slug), exact: true });
			toast.success(offered ? "Group included for workspaces" : "Group is no longer included");
		},
		onError: (error: unknown) => {
			queryClient.removeQueries({ queryKey: detailKey("area", slug), exact: true });
			invalidateCatalog();
			toast.error(
				problemStatusOf(error) === 412
					? "The catalog changed before this action was saved. We reloaded the group."
					: "Couldn't update the group",
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
	const invalidateStructure = invalidateCatalog;
	const structureError = (error: unknown) => {
		toast.error(
			problemStatusOf(error) === 412
				? "The catalog order changed before this move was saved. We reloaded the latest order."
				: "Couldn't save the catalog order",
			{ description: problemDetailOf(error) },
		);
	};
	const reorderAreas = useMutation({
		...adminReorderCuratedAreasMutation(),
		scope: STRUCTURE_SCOPE,
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			const previous = queryClient.getQueryData<Catalog>(adminGetCuratedCatalogQueryKey());
			if (previous) {
				queryClient.setQueryData(
					adminGetCuratedCatalogQueryKey(),
					reorderCuratedAreas(previous, variables.body.orderedSlugs),
				);
			}
			return { previous };
		},
		onSuccess: (catalog) => queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), catalog),
		onError: (error, _variables, context) => {
			if (context?.previous) {
				queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), context.previous);
			}
			structureError(error);
		},
		onSettled: invalidateStructure,
	});
	const reorderPractices = useMutation({
		...adminReorderCuratedPracticesMutation(),
		scope: STRUCTURE_SCOPE,
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			const previous = queryClient.getQueryData<Catalog>(adminGetCuratedCatalogQueryKey());
			if (previous) {
				const areaSlug = variables.body.areaSlug ?? null;
				queryClient.setQueryData(
					adminGetCuratedCatalogQueryKey(),
					reorderCuratedPractices(previous, areaSlug, variables.body.orderedSlugs),
				);
			}
			return { previous };
		},
		onSuccess: (catalog) => queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), catalog),
		onError: (error, _variables, context) => {
			if (context?.previous) {
				queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), context.previous);
			}
			structureError(error);
		},
		onSettled: invalidateStructure,
	});
	const placePractice = useMutation({
		...adminPlaceCuratedPracticeMutation(),
		scope: STRUCTURE_SCOPE,
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			const previous = queryClient.getQueryData<Catalog>(adminGetCuratedCatalogQueryKey());
			if (previous) {
				queryClient.setQueryData(
					adminGetCuratedCatalogQueryKey(),
					placeCuratedPractice(
						previous,
						variables.path.slug,
						variables.body.areaSlug ?? null,
						variables.body.position,
					),
				);
			}
			return { previous };
		},
		onSuccess: (catalog, variables) => {
			queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), catalog);
			queryClient.removeQueries({
				queryKey: adminGetCuratedPracticeQueryKey({ path: { slug: variables.path.slug } }),
				exact: true,
			});
		},
		onError: (error, _variables, context) => {
			if (context?.previous) {
				queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), context.previous);
			}
			structureError(error);
		},
		onSettled: invalidateStructure,
	});
	const resetOrder = useMutation({
		...adminResetCuratedCatalogOrderMutation(),
		scope: STRUCTURE_SCOPE,
		onSuccess: (catalog) => {
			queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), catalog);
			toast.success("Hephaestus order restored");
		},
		onError: structureError,
		onSettled: invalidateStructure,
	});
	const pendingPracticeSlugs = usePendingMutationIds<{ path: { slug: string } }, string>(
		PRACTICE_STATUS_KEY,
		(variables) => variables.path.slug,
	);
	const pendingAreaSlugs = usePendingMutationIds<{ path: { slug: string } }, string>(
		AREA_STATUS_KEY,
		(variables) => variables.path.slug,
	);
	const structurePending =
		reorderAreas.isPending ||
		reorderPractices.isPending ||
		placePractice.isPending ||
		resetOrder.isPending;
	const writePending =
		structurePending || pendingAreaSlugs.size > 0 || pendingPracticeSlugs.size > 0;

	return (
		<PageLayout>
			<PageHeader
				icon={<LibraryBig />}
				title="Practice catalog"
				description="Choose which areas and practices workspace administrators can add. Catalog changes never rewrite existing workspace practices."
				actions={
					<div className="flex flex-wrap gap-2">
						{writePending ? (
							<Button variant="outline" disabled>
								<Plus className="mr-1.5 size-4" aria-hidden />
								Create group
							</Button>
						) : (
							<Link
								from={Route.fullPath}
								to="/admin/catalog/areas/new"
								search={(previous) => previous}
								className={buttonVariants({ variant: "outline" })}
							>
								<Plus className="mr-1.5 size-4" aria-hidden />
								Create group
							</Link>
						)}
						{writePending ? (
							<Button disabled>
								<Plus className="mr-1.5 size-4" aria-hidden />
								Create practice
							</Button>
						) : (
							<Link
								from={Route.fullPath}
								to="/admin/catalog/practices/new"
								search={(previous) => previous}
								className={buttonVariants()}
							>
								<Plus className="mr-1.5 size-4" aria-hidden />
								Create practice
							</Link>
						)}
					</div>
				}
			/>

			{catalogQuery.isPending ? (
				<PracticeTreeSkeleton areas={3} practicesPerArea={3} />
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
					customOrder={catalogQuery.data.customOrder}
					pendingPracticeSlugs={pendingPracticeSlugs}
					pendingAreaSlugs={pendingAreaSlugs}
					writePending={writePending}
					onSearchChange={(next) => navigate({ search: next, replace: true })}
					onPracticeStatusChange={(practice: CuratedPracticeSummary, offered) => {
						const parent = practice.areaSlug
							? catalogQuery.data.areas.find((area) => area.slug === practice.areaSlug)
							: undefined;
						const availabilityMessage = practice.areaSlug
							? parent
								? parent.status.offered
									? undefined
									: "Practice will be included when its group is included"
								: "Move the practice to an included group first"
							: undefined;
						updatePracticeStatus.mutate(
							{
								path: { slug: practice.slug },
								headers: { "If-Match": `"${practice.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onPracticeStatusSettled(
								practice.slug,
								offered,
								offered ? availabilityMessage : undefined,
							),
						);
					}}
					onAreaStatusChange={(area: CuratedArea, offered) =>
						updateAreaStatus.mutate(
							{
								path: { slug: area.slug },
								headers: { "If-Match": `"${catalogQuery.data.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onAreaStatusSettled(area.slug, offered),
						)
					}
					onReorderAreas={(orderedSlugs) =>
						reorderAreas.mutate({
							headers: { "If-Match": `"${catalogQuery.data.etag}"` },
							body: { orderedSlugs },
						})
					}
					onResetOrder={() =>
						resetOrder.mutate({
							headers: { "If-Match": `"${catalogQuery.data.etag}"` },
						})
					}
					onPlacePractice={(practiceSlug, areaSlug, position) => {
						const practice = catalogQuery.data.practices.find(
							(candidate) => candidate.slug === practiceSlug,
						);
						if (!practice) return;
						const optimistic = placeCuratedPractice(
							catalogQuery.data,
							practiceSlug,
							areaSlug,
							position,
						);
						if ((practice.areaSlug ?? null) === areaSlug) {
							reorderPractices.mutate({
								headers: { "If-Match": `"${catalogQuery.data.etag}"` },
								body: { areaSlug, orderedSlugs: orderedPracticeSlugs(optimistic, areaSlug) },
							});
							return;
						}
						placePractice.mutate({
							path: { slug: practiceSlug },
							headers: { "If-Match": `"${catalogQuery.data.etag}"` },
							body: { areaSlug: areaSlug ?? undefined, position },
						});
					}}
				/>
			)}
		</PageLayout>
	);
}
