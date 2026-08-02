import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	adminCreateCuratedPracticeMutation,
	adminGetCuratedCatalogOptions,
	adminGetCuratedCatalogQueryKey,
} from "@/api/@tanstack/react-query.gen";
import {
	CuratedPracticeForm,
	type CuratedPracticeFormValue,
} from "@/components/admin/curated-catalog/CuratedPracticeForm";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/practices/new")({
	head: instanceAdminHead("Create practice"),
	component: NewCuratedPracticePage,
});

function NewCuratedPracticePage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const queryClient = useQueryClient();
	const catalogQuery = useQuery({ ...adminGetCuratedCatalogOptions() });
	const createPractice = useMutation({
		...adminCreateCuratedPracticeMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success("Practice created");
			navigate({ to: "/admin/catalog" });
		},
		onError: (error) =>
			toast.error("Couldn't create the practice", { description: problemDetailOf(error) }),
	});

	if (catalogQuery.isPending) {
		return (
			<PageLayout>
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PageLayout>
		);
	}
	if (catalogQuery.isError) {
		return (
			<PageLayout>
				<QueryErrorAlert
					error={catalogQuery.error}
					title="Couldn't load the areas"
					onRetry={() => catalogQuery.refetch()}
				/>
			</PageLayout>
		);
	}

	return (
		<CuratedPracticeForm
			mode="create"
			areas={catalogQuery.data.areas.map((area) => ({
				slug: area.slug,
				name: area.definition.name,
			}))}
			isPending={createPractice.isPending}
			onSubmit={({ slug, ...definition }: CuratedPracticeFormValue) =>
				createPractice.mutate({ body: { slug, definition } })
			}
		/>
	);
}
