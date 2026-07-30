import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	createPracticeMutation,
	listAreasOptions,
	listPracticesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { CreatePracticeRequest, Practice } from "@/api/types.gen";
import { PracticeForm } from "@/components/admin/practices/PracticeForm";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Spinner } from "@/components/ui/spinner";
import { practiceCatalogStructureScope, upsertPractice } from "@/hooks/practice-catalog-cache";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/new")({
	head: workspaceAdminHead("New practice"),
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
			toast.success("Practice created successfully");
			navigate({ to: ".." });
		} catch (error) {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			toast.error(
				status === 409
					? "A practice with this slug already exists in this workspace"
					: "Failed to create practice",
			);
		}
	};

	const handleCancel = () => {
		navigate({ to: ".." });
	};

	if (areasQuery.isPending) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spinner className="size-8" />
			</div>
		);
	}
	if (areasQuery.isError) {
		return (
			<div className="mx-auto w-full max-w-3xl">
				<QueryErrorAlert
					error={areasQuery.error}
					title="Couldn't load practice areas"
					onRetry={() => areasQuery.refetch()}
				/>
			</div>
		);
	}

	return (
		<PracticeForm
			mode="create"
			workspaceSlug={workspaceSlug}
			areas={areasQuery.data}
			onSubmit={handleSubmit}
			onCancel={handleCancel}
			isPending={createPractice.isPending}
		/>
	);
}
