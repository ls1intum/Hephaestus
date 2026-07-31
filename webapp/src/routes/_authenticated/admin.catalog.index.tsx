import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { LibraryBig, Plus } from "lucide-react";
import { toast } from "sonner";
import {
	adminGetCuratedPracticeQueryKey,
	adminListCuratedPracticeAreasOptions,
	adminListCuratedPracticesOptions,
	adminListCuratedPracticesQueryKey,
	adminUpdateCuratedPracticeStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import {
	CuratedPracticeCatalog,
	type CuratedPracticeCatalogItem,
	type CuratedPracticeStatus,
} from "@/components/admin/curated-practices/CuratedPracticeCatalog";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/")({
	head: instanceAdminHead("Curated catalog"),
	component: AdminCuratedCatalogPage,
});

const STATUS_MUTATION_KEY = ["adminWriteCuratedPracticeStatus"];

function AdminCuratedCatalogPage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const search = Route.useSearch();
	const queryClient = useQueryClient();
	const areasQuery = useQuery({ ...adminListCuratedPracticeAreasOptions() });
	const catalogQuery = useQuery({ ...adminListCuratedPracticesOptions() });
	const updateStatus = useMutation({
		...filedUnder(STATUS_MUTATION_KEY, adminUpdateCuratedPracticeStatusMutation()),
		onSuccess: (updated, variables) => {
			queryClient.setQueryData<CuratedPracticeCatalogItem[]>(
				adminListCuratedPracticesQueryKey(),
				(practices) =>
					practices?.map((practice) =>
						practice.slug === updated.slug ? { ...practice, ...updated } : practice,
					),
			);
			queryClient.setQueryData(
				adminGetCuratedPracticeQueryKey({ path: { slug: updated.slug } }),
				updated,
			);
			void queryClient.invalidateQueries({ queryKey: adminListCuratedPracticesQueryKey() });
			toast.success(
				variables.body.status === "AVAILABLE" ? "Practice restored" : "Practice retired",
			);
		},
		onError: (error, variables) => {
			if (problemStatusOf(error) === 412) {
				void queryClient.invalidateQueries({ queryKey: adminListCuratedPracticesQueryKey() });
				void queryClient.invalidateQueries({
					queryKey: adminGetCuratedPracticeQueryKey({ path: variables.path }),
				});
			}
			toast.error(
				problemStatusOf(error) === 412
					? "The practice changed before its status could be updated"
					: "Couldn't update the practice status",
				{ description: problemDetailOf(error) },
			);
		},
	});
	const pendingSlugs = usePendingMutationIds<{ path: { slug: string } }, string>(
		STATUS_MUTATION_KEY,
		(variables) => variables.path.slug,
	);

	const changeStatus = (practice: CuratedPracticeCatalogItem, status: CuratedPracticeStatus) => {
		updateStatus.mutate({
			path: { slug: practice.slug },
			headers: { "If-Match": `"v${practice.version}"` },
			body: { status },
		});
	};

	return (
		<PageLayout>
			<PageHeader
				icon={<LibraryBig />}
				title="Curated practice catalog"
				description="Manage the versioned practices available across this instance."
				actions={
					<Button
						nativeButton={false}
						render={
							<Link from={Route.fullPath} to="/admin/catalog/new" search={(previous) => previous} />
						}
					>
						<Plus className="size-4" aria-hidden />
						Create practice
					</Button>
				}
			/>

			{catalogQuery.isPending || areasQuery.isPending ? (
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			) : catalogQuery.isError || areasQuery.isError ? (
				<QueryErrorAlert
					error={catalogQuery.error ?? areasQuery.error}
					title="Couldn't load the curated catalog"
					onRetry={() => {
						catalogQuery.refetch();
						areasQuery.refetch();
					}}
				/>
			) : (
				<CuratedPracticeCatalog
					areas={areasQuery.data}
					practices={catalogQuery.data}
					search={search}
					pendingSlugs={pendingSlugs}
					onSearchChange={(next) => navigate({ search: next, replace: true })}
					onStatusChange={changeStatus}
				/>
			)}
		</PageLayout>
	);
}
