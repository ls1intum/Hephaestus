import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
	adminCreateCuratedAreaMutation,
	adminGetCuratedCatalogQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { CuratedAreaForm } from "@/components/admin/curated-catalog/CuratedAreaForm";
import { CuratedFormLevel } from "@/components/admin/curated-catalog/CuratedFormLevel";
import { LevelCancel } from "@/components/core/detail-drawer/LevelCancel";
import { problemDetailOf } from "@/lib/problem-detail";

export interface CuratedAreaCreateLevelProps {
	nested?: boolean;
	onDone: () => void;
}

export function CuratedAreaCreateLevel({ nested, onDone }: CuratedAreaCreateLevelProps) {
	const queryClient = useQueryClient();
	const createArea = useMutation({
		...adminCreateCuratedAreaMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success("Group created");
			onDone();
		},
		onError: (error) =>
			toast.error("Couldn't create the group", { description: problemDetailOf(error) }),
	});

	return (
		<CuratedFormLevel kind="area-new" nested={nested}>
			<CuratedAreaForm
				mode="create"
				cancel={<LevelCancel />}
				isPending={createArea.isPending}
				onSubmit={({ slug, ...definition }) => createArea.mutate({ body: { slug, definition } })}
			/>
		</CuratedFormLevel>
	);
}
