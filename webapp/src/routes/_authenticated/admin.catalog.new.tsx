import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	adminCreateCuratedPracticeMutation,
	adminListCuratedPracticeAreasOptions,
	adminListCuratedPracticesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import {
	CuratedPracticeForm,
	type CuratedPracticeFormValue,
} from "@/components/admin/curated-practices/CuratedPracticeForm";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/new")({
	head: instanceAdminHead("New curated practice"),
	component: NewCuratedPracticePage,
});

function NewCuratedPracticePage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const queryClient = useQueryClient();
	const areasQuery = useQuery({ ...adminListCuratedPracticeAreasOptions() });
	const createPractice = useMutation({
		...adminCreateCuratedPracticeMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminListCuratedPracticesQueryKey() });
			toast.success("Curated practice created");
			navigate({ to: "/admin/catalog" });
		},
		onError: (error) =>
			toast.error("Couldn't create the curated practice", { description: problemDetailOf(error) }),
	});

	const submit = (value: CuratedPracticeFormValue) => createPractice.mutate({ body: value });

	if (areasQuery.isPending) {
		return (
			<PageLayout>
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PageLayout>
		);
	}
	if (areasQuery.isError) {
		return (
			<PageLayout>
				<QueryErrorAlert
					error={areasQuery.error}
					title="Couldn't load the curated practice areas"
					onRetry={() => areasQuery.refetch()}
				/>
			</PageLayout>
		);
	}

	return (
		<CuratedPracticeForm
			mode="create"
			areas={areasQuery.data}
			isPending={createPractice.isPending}
			onSubmit={submit}
		/>
	);
}
