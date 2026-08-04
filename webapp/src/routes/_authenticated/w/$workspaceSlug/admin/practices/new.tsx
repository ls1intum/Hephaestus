import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	createPracticeMutation,
	getPracticeEvidenceOptionsOptions,
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
			query: { activeOnly: true },
		}),
	});
	const evidenceQuery = useQuery({
		...getPracticeEvidenceOptionsOptions({ path: { workspaceSlug } }),
	});

	const practicesQueryKey = listPracticesQueryKey({ path: { workspaceSlug } });
	const createPractice = useMutation({
		...createPracticeMutation(),
		scope: practiceCatalogStructureScope(workspaceSlug),
		onMutate: () => queryClient.cancelQueries({ queryKey: practicesQueryKey }),
	});

	const handleSubmit = async (data: CreatePracticeRequest, areaSlug: string | null) => {
		try {
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
		} catch (error) {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			toast.error(
				status === 409
					? "A practice with this identifier already exists in this workspace"
					: "Couldn't create the practice",
			);
		}
	};

	if (areasQuery.isPending || evidenceQuery.isPending) {
		return (
			<PracticeFormShell mode="create" workspaceSlug={workspaceSlug}>
				<div className="flex h-64 max-w-3xl items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PracticeFormShell>
		);
	}
	if (areasQuery.isError || evidenceQuery.isError) {
		return (
			<PracticeFormShell mode="create" workspaceSlug={workspaceSlug}>
				<div className="max-w-3xl">
					<QueryErrorAlert
						error={areasQuery.error ?? evidenceQuery.error}
						title="Couldn't load the practice editor"
						onRetry={() => {
							areasQuery.refetch();
							evidenceQuery.refetch();
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
			evidenceAuthoring={evidenceQuery.data}
			onSubmit={handleSubmit}
			isPending={createPractice.isPending}
		/>
	);
}
