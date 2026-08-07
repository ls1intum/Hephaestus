import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	createPracticeMutation,
	getPracticeDefinitionOptionsOptions,
	listAreasOptions,
	listPracticesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { CreatePracticeRequest, Practice } from "@/api/types.gen";
import { PracticeForm, PracticeFormShell } from "@/components/admin/practices/PracticeForm";
import {
	PRACTICE_SEARCH_PARAMS,
	practiceSearchSchema,
} from "@/components/admin/practices/practice-search";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Spinner } from "@/components/ui/spinner";
import { practiceCatalogStructureScope, upsertPractice } from "@/hooks/practice-catalog-cache";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/new")({
	head: workspaceAdminHead("Create practice"),
	validateSearch: practiceSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SEARCH_PARAMS)] },
	component: CreatePracticeContainer,
});

function CreatePracticeContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();

	const areasQuery = useQuery({
		...listAreasOptions({
			path: { workspaceSlug },
			query: { visibleInPracticeDashboardsOnly: true },
		}),
	});
	const definitionOptionsQuery = useQuery({
		...getPracticeDefinitionOptionsOptions({ path: { workspaceSlug } }),
	});

	const practicesQueryKey = listPracticesQueryKey({ path: { workspaceSlug } });
	const createPractice = useMutation({
		...createPracticeMutation(),
		scope: practiceCatalogStructureScope(workspaceSlug),
		onMutate: () => queryClient.cancelQueries({ queryKey: practicesQueryKey }),
		onError: (error) => {
			const status = problemStatusOf(error);
			toast.error(
				status === 409
					? "A practice with this identifier already exists in this workspace"
					: "Couldn't create the practice",
			);
		},
	});

	// Reports the failure by rejecting rather than swallowing it: the form holds its unsaved-changes
	// guard down from submit until it hears one way or the other, so a caught-and-resolved failure
	// would leave a lost draft unguarded.
	const handleSubmit = async (data: CreatePracticeRequest, areaSlug: string | null) => {
		const created = await createPractice.mutateAsync({
			path: { workspaceSlug },
			body: { ...data, areaSlug },
		});
		queryClient.setQueryData<Practice[]>(practicesQueryKey, (practices) =>
			practices ? upsertPractice(practices, created) : practices,
		);
		void queryClient.invalidateQueries({
			queryKey: practicesQueryKey,
		});
		toast.success("Practice created");
		navigate({ to: ".." });
	};

	if (areasQuery.isPending || definitionOptionsQuery.isPending) {
		return (
			<PracticeFormShell mode="create" workspaceSlug={workspaceSlug}>
				<div className="flex h-64 max-w-3xl items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PracticeFormShell>
		);
	}
	if (areasQuery.isError || definitionOptionsQuery.isError) {
		return (
			<PracticeFormShell mode="create" workspaceSlug={workspaceSlug}>
				<div className="max-w-3xl">
					<QueryErrorAlert
						error={areasQuery.error ?? definitionOptionsQuery.error}
						title="Couldn't load the practice editor"
						onRetry={() => {
							areasQuery.refetch();
							definitionOptionsQuery.refetch();
						}}
					/>
				</div>
			</PracticeFormShell>
		);
	}

	return (
		<PracticeForm
			mode="create"
			workspaceSlug={workspaceSlug}
			areas={areasQuery.data}
			definitionOptions={definitionOptionsQuery.data}
			onSubmit={handleSubmit}
			isPending={createPractice.isPending}
		/>
	);
}
