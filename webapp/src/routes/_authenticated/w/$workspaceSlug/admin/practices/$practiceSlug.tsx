import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getPracticeDefinitionOptionsOptions,
	getPracticeOptions,
	getPracticeQueryKey,
	listAreasOptions,
	listPracticeEvidenceOutcomesOptions,
	listPracticesQueryKey,
	updatePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice, UpdatePracticeRequest } from "@/api/types.gen";
import { PracticeForm, PracticeFormShell } from "@/components/admin/practices/PracticeForm";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import {
	PRACTICE_SETUP_SEARCH_PARAMS,
	practiceSetupSearchSchema,
} from "@/components/admin/practices/practice-search";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	patchPractice,
	practiceCatalogStructureScope,
	selectPracticePatch,
} from "@/hooks/practice-catalog-cache";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/$practiceSlug",
)({
	head: workspaceAdminHead("Edit practice"),
	validateSearch: practiceSetupSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SETUP_SEARCH_PARAMS)] },
	component: EditPracticeContainer,
});

function EditPracticeContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { workspaceSlug, practiceSlug } = Route.useParams();
	const detailQueryKey = getPracticeQueryKey({
		path: { workspaceSlug, practiceSlug },
	});
	const listQueryKey = listPracticesQueryKey({ path: { workspaceSlug } });

	const practiceQueryOptions = getPracticeOptions({
		path: { workspaceSlug, practiceSlug },
	});
	const practiceQuery = useQuery({
		...practiceQueryOptions,
	});

	const areasQuery = useQuery({
		...listAreasOptions({
			path: { workspaceSlug },
			query: { visibleInPracticeDashboardsOnly: true },
		}),
	});
	const definitionOptionsQuery = useQuery({
		...getPracticeDefinitionOptionsOptions({ path: { workspaceSlug } }),
	});
	// Deliberately outside the pending/error gates below: how past reviews turned out is context, not
	// something the author needs in order to edit the requirements.
	const evidenceOutcomesQuery = useQuery({
		...listPracticeEvidenceOutcomesOptions({ path: { workspaceSlug } }),
	});

	const updatePractice = useMutation({
		...updatePracticeMutation(),
		scope: practiceCatalogStructureScope(workspaceSlug),
		onMutate: async () => {
			await Promise.all([
				queryClient.cancelQueries({ queryKey: detailQueryKey }),
				queryClient.cancelQueries({ queryKey: listQueryKey }),
			]);
		},
		onError: () => toast.error("Couldn't save the practice"),
	});

	// Rejects rather than swallowing the failure: the form holds its unsaved-changes guard down from
	// submit until it hears one way or the other, so resolving on failure would lose the draft.
	const handleSubmit = async (
		slug: string,
		data: UpdatePracticeRequest,
		areaSlug: string | null,
	) => {
		const request = { ...data, area: { areaSlug } };
		const updated = await updatePractice.mutateAsync({
			path: { workspaceSlug, practiceSlug: slug },
			body: request,
		});
		queryClient.setQueryData(detailQueryKey, updated);
		queryClient.setQueryData<Practice[]>(listQueryKey, (practices) =>
			practices
				? patchPractice(practices, updated.slug, selectPracticePatch(updated, request))
				: practices,
		);
		void queryClient.invalidateQueries({ queryKey: detailQueryKey });
		void queryClient.invalidateQueries({ queryKey: listQueryKey });
		toast.success("Practice saved");
		navigate({ to: "..", search: (previous) => previous });
	};

	if (practiceQuery.isPending || areasQuery.isPending || definitionOptionsQuery.isPending) {
		return (
			<PracticeFormShell mode="edit" workspaceSlug={workspaceSlug}>
				<PracticeDefinitionSkeleton />
			</PracticeFormShell>
		);
	}
	if (practiceQuery.isError || areasQuery.isError || definitionOptionsQuery.isError) {
		return (
			<PracticeFormShell mode="edit" workspaceSlug={workspaceSlug}>
				<div className="max-w-3xl">
					<QueryErrorAlert
						error={practiceQuery.error ?? areasQuery.error ?? definitionOptionsQuery.error}
						title="Couldn't load the practice"
						onRetry={() => {
							practiceQuery.refetch();
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
			mode="edit"
			workspaceSlug={workspaceSlug}
			initialData={practiceQuery.data}
			areas={areasQuery.data}
			definitionOptions={definitionOptionsQuery.data}
			onSubmit={handleSubmit}
			isPending={updatePractice.isPending}
			evidenceOutcome={evidenceOutcomesQuery.data?.find(
				(entry) => entry.practiceSlug === practiceSlug,
			)}
		/>
	);
}
