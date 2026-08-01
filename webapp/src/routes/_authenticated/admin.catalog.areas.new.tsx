import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	adminCreateCuratedAreaMutation,
	adminGetCuratedCatalogQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { CuratedAreaForm } from "@/components/admin/curated-catalog/CuratedAreaForm";
import { PageLayout } from "@/components/core/PageLayout";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/areas/new")({
	head: instanceAdminHead("New catalog area"),
	component: NewCuratedAreaPage,
});

function NewCuratedAreaPage() {
	const navigate = useNavigate({ from: Route.fullPath });
	const queryClient = useQueryClient();
	const createArea = useMutation({
		...adminCreateCuratedAreaMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success("Area added to the catalog");
			navigate({ to: "/admin/catalog" });
		},
		onError: (error) =>
			toast.error("Couldn't add the area", { description: problemDetailOf(error) }),
	});

	return (
		<PageLayout>
			<CuratedAreaForm
				mode="create"
				isPending={createArea.isPending}
				onSubmit={({ slug, ...definition }) => createArea.mutate({ body: { slug, definition } })}
			/>
		</PageLayout>
	);
}
