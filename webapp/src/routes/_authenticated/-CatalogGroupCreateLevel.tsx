import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
	createCatalogGroupMutation,
	getInstanceCatalogQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { CatalogFormLevel } from "@/components/admin/catalog/CatalogFormLevel";
import { CatalogGroupForm } from "@/components/admin/catalog/CatalogGroupForm";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { problemDetailOf } from "@/lib/problem-detail";

export interface CatalogGroupCreateLevelProps {
	nested?: boolean;
	/** Where the level sits, from the host's `levelPathAt`. */
	path: LevelPath;
	onDone: () => void;
}

export function CatalogGroupCreateLevel({ nested, path, onDone }: CatalogGroupCreateLevelProps) {
	const queryClient = useQueryClient();
	const createGroup = useMutation({
		...createCatalogGroupMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: getInstanceCatalogQueryKey() });
			toast.success("Group created");
			onDone();
		},
		onError: (error) =>
			toast.error("We could not create the group", { description: problemDetailOf(error) }),
	});

	return (
		<CatalogFormLevel kind="group-new" nested={nested} path={path}>
			<CatalogGroupForm
				mode="create"
				cancel={<LevelCancel />}
				isPending={createGroup.isPending}
				onSubmit={(group) => createGroup.mutate({ body: { ...group, position: null } })}
			/>
		</CatalogFormLevel>
	);
}
