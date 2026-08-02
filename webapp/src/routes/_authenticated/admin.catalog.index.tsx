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
	adminUpdateCuratedAreaStatusMutation,
	adminUpdateCuratedPracticeStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CuratedCatalog as Catalog,
	CuratedArea,
	CuratedPracticeSummary,
} from "@/api/types.gen";
import { CuratedCatalog } from "@/components/admin/curated-catalog/CuratedCatalog";
import {
	orderedPracticeSlugs,
	placeCuratedPractice,
	reorderCuratedAreas,
	reorderCuratedPractices,
} from "@/components/admin/curated-catalog/curated-catalog-cache";
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
const STRUCTURE_SCOPE = { id: "admin-curated-catalog-structure" };

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

	const onStatusSettled = (
		kind: "practice" | "area",
		slug: string,
		offered: boolean,
		successMessage?: string,
	) => ({
		onSuccess: () => {
			refreshCatalogAnd(kind, slug);
			const noun = kind === "practice" ? "Practice" : "Area";
			toast.success(
				successMessage ??
					(offered ? `${noun} included in new workspaces` : `${noun} excluded from new workspaces`),
			);
		},
		onError: (error: unknown) => {
			refreshCatalogAnd(kind, slug);
			toast.error(
				problemStatusOf(error) === 412
					? `The catalog changed before this action was saved. We reloaded the ${kind}.`
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
	const invalidateStructure = () =>
		void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
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
		onSuccess: (catalog) => queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), catalog),
		onError: (error, _variables, context) => {
			if (context?.previous) {
				queryClient.setQueryData(adminGetCuratedCatalogQueryKey(), context.previous);
			}
			structureError(error);
		},
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
		reorderAreas.isPending || reorderPractices.isPending || placePractice.isPending;

	return (
		<PageLayout>
			<PageHeader
				icon={<LibraryBig />}
				title="Practice catalog"
				description="Set what each new workspace starts with. Hephaestus defaults update automatically until you customize them. Existing workspaces never change automatically."
				actions={
					<div className="flex flex-wrap gap-2">
						<Link
							from={Route.fullPath}
							to="/admin/catalog/areas/new"
							search={(previous) => previous}
							className={buttonVariants({ variant: "outline" })}
						>
							<Plus className="mr-1.5 size-4" aria-hidden />
							Create area
						</Link>
						<Link
							from={Route.fullPath}
							to="/admin/catalog/practices/new"
							search={(previous) => previous}
							className={buttonVariants()}
						>
							<Plus className="mr-1.5 size-4" aria-hidden />
							Create practice
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
					structurePending={structurePending}
					onSearchChange={(next) => navigate({ search: next, replace: true })}
					onPracticeStatusChange={(practice: CuratedPracticeSummary, offered) => {
						const parent = practice.areaSlug
							? catalogQuery.data.areas.find((area) => area.slug === practice.areaSlug)
							: undefined;
						const availabilityMessage = practice.areaSlug
							? parent
								? parent.status.offered
									? undefined
									: "Practice will be included when its area is included"
								: "Move the practice to an included area first"
							: undefined;
						updatePracticeStatus.mutate(
							{
								path: { slug: practice.slug },
								headers: { "If-Match": `"${practice.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onStatusSettled(
								"practice",
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
								headers: { "If-Match": `"${area.status.etag}"` },
								body: { status: offered ? "AVAILABLE" : "RETIRED" },
							},
							onStatusSettled("area", area.slug, offered),
						)
					}
					onReorderAreas={(orderedSlugs) =>
						reorderAreas.mutate({
							headers: { "If-Match": `"${catalogQuery.data.etag}"` },
							body: { orderedSlugs },
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
