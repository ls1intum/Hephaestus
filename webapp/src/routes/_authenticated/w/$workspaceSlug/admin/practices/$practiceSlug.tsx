import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getPracticeOptions,
	getPracticeQueryKey,
	listAreasOptions,
	listPracticesQueryKey,
	updatePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice, UpdatePracticeRequest } from "@/api/types.gen";
import { PracticeForm, PracticeFormShell } from "@/components/admin/practices/PracticeForm";
import {
	PRACTICE_SEARCH_PARAMS,
	practiceSearchSchema,
} from "@/components/admin/practices/practice-search";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Spinner } from "@/components/ui/spinner";
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
	validateSearch: practiceSearchSchema,
	search: { middlewares: [retainSearchParams(PRACTICE_SEARCH_PARAMS)] },
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
			query: { activeOnly: true },
		}),
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
	});

	const handleSubmit = async (
		slug: string,
		data: UpdatePracticeRequest,
		areaSlug: string | null,
	) => {
		try {
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
			toast.success("Practice updated successfully");
			navigate({ to: ".." });
		} catch {
			toast.error("Failed to update practice");
		}
	};

	if (practiceQuery.isPending || areasQuery.isPending) {
		return (
			<PracticeFormShell mode="edit" workspaceSlug={workspaceSlug}>
				<div className="flex h-64 max-w-3xl items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PracticeFormShell>
		);
	}
	if (practiceQuery.isError || areasQuery.isError) {
		return (
			<PracticeFormShell mode="edit" workspaceSlug={workspaceSlug}>
				<div className="max-w-3xl">
					<QueryErrorAlert
						error={practiceQuery.error ?? areasQuery.error}
						title="Couldn't load the practice"
						onRetry={() => {
							practiceQuery.refetch();
							areasQuery.refetch();
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
			onSubmit={handleSubmit}
			isPending={updatePractice.isPending}
		/>
	);
}
