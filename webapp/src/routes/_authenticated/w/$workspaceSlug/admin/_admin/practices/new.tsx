import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import { createPracticeMutation, listPracticesQueryKey } from "@/api/@tanstack/react-query.gen";
import type { CreatePracticeRequest } from "@/api/types.gen";
import { PracticeForm } from "@/components/admin/practices/PracticeForm";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices/new")(
	{
		component: CreatePracticeContainer,
	},
);

function CreatePracticeContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();

	const createPractice = useMutation({
		...createPracticeMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: listPracticesQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
			});
			toast.success("Practice created successfully");
			navigate({ to: ".." });
		},
		onError: (error) => {
			const status =
				typeof error === "object" && error !== null && "status" in error
					? (error as { status: number }).status
					: undefined;
			if (status === 409) {
				toast.error("A practice with this slug already exists in this workspace");
			} else {
				toast.error("Failed to create practice");
			}
		},
	});

	const handleSubmit = (data: CreatePracticeRequest) => {
		if (!workspaceSlug) return;
		createPractice.mutate({
			path: { workspaceSlug },
			body: data,
		});
	};

	const handleCancel = () => {
		navigate({ to: ".." });
	};

	return (
		<PracticeForm
			mode="create"
			onSubmit={handleSubmit}
			onCancel={handleCancel}
			isPending={createPractice.isPending}
		/>
	);
}
