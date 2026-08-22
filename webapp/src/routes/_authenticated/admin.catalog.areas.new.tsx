import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	adminCreateCuratedAreaMutation,
	adminGetCuratedCatalogQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { CuratedAreaForm } from "@/components/admin/curated-catalog/CuratedAreaForm";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/areas/new")({
	head: instanceAdminHead("Create group"),
	component: NewCuratedAreaPage,
});

function NewCuratedAreaPage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const queryClient = useQueryClient();
	const createArea = useMutation({
		...adminCreateCuratedAreaMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success("Group created");
			navigate({ to: "/admin/catalog" });
		},
		onError: (error) =>
			toast.error("Couldn't create the group", { description: problemDetailOf(error) }),
	});

	return (
		<CuratedAreaForm
			mode="create"
			isPending={createArea.isPending}
			onSubmit={({ slug, ...definition }) => createArea.mutate({ body: { slug, definition } })}
		/>
	);
}
