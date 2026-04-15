import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Navigate, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getPracticeOptions,
	listPracticesQueryKey,
	updatePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { UpdatePracticeRequest } from "@/api/types.gen";
import { PracticeForm } from "@/components/admin/practices/PracticeForm";
import { Spinner } from "@/components/ui/spinner";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/practices/$practiceSlug",
)({
	component: EditPracticeContainer,
	staticData: {
		workspaceSwitch: {
			fallbackTo: "/w/$workspaceSlug/admin/practices",
		},
	},
});

function EditPracticeContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { practiceSlug, workspaceSlug } = Route.useParams();

	const practiceQueryOptions = getPracticeOptions({
		path: { workspaceSlug: workspaceSlug ?? "", practiceSlug },
	});
	const { data: practice, isLoading } = useQuery({
		...practiceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const updatePractice = useMutation({
		...updatePracticeMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: listPracticesQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
			});
			toast.success("Practice updated successfully");
			navigate({ to: ".." });
		},
		onError: () => {
			toast.error("Failed to update practice");
		},
	});

	const handleSubmit = (slug: string, data: UpdatePracticeRequest) => {
		if (!workspaceSlug) return;
		updatePractice.mutate({
			path: { workspaceSlug, practiceSlug: slug },
			body: data,
		});
	};

	const handleCancel = () => {
		navigate({ to: ".." });
	};

	if (isLoading) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	if (!practice) {
		return <Navigate to="/w/$workspaceSlug/admin/practices" params={{ workspaceSlug }} replace />;
	}

	return (
		<PracticeForm
			mode="edit"
			initialData={practice}
			onSubmit={handleSubmit}
			onCancel={handleCancel}
			isPending={updatePractice.isPending}
		/>
	);
}
