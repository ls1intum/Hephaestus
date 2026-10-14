import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { toast } from "sonner";

import {
	createCatalogPracticeMutation,
	getInstanceCatalogOptions,
	getInstanceCatalogQueryKey,
	getInstanceDefinitionOptionsOptions,
} from "@/api/@tanstack/react-query.gen";
import { CatalogFormLevel } from "@/components/admin/catalog/CatalogFormLevel";
import { CatalogPracticeForm } from "@/components/admin/catalog/CatalogPracticeForm";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { DrawerBody } from "@/components/ui/drawer";
import { problemDetailOf } from "@/lib/problem-detail";

export interface CatalogPracticeCreateLevelProps {
	nested?: boolean;
	/** Where the level sits, from the host's `levelPathAt`. */
	path: LevelPath;
	onDone: () => void;
}

export function CatalogPracticeCreateLevel({
	nested,
	path,
	onDone,
}: CatalogPracticeCreateLevelProps) {
	const queryClient = useQueryClient();
	const catalogQuery = useQuery({ ...getInstanceCatalogOptions() });
	const definitionOptionsQuery = useQuery({ ...getInstanceDefinitionOptionsOptions() });
	const createPractice = useMutation({
		...createCatalogPracticeMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: getInstanceCatalogQueryKey() });
			toast.success("Practice created");
			onDone();
		},
		onError: (error) =>
			toast.error("We could not create the practice", { description: problemDetailOf(error) }),
	});

	let body: ReactNode;
	if (catalogQuery.isPending || definitionOptionsQuery.isPending) {
		body = (
			<DrawerBody>
				<PracticeDefinitionSkeleton />
			</DrawerBody>
		);
	} else if (catalogQuery.isError || definitionOptionsQuery.isError) {
		body = (
			<DrawerBody>
				<QueryErrorAlert
					error={catalogQuery.error ?? definitionOptionsQuery.error}
					title="We could not load the practice editor"
					onRetry={() => {
						void catalogQuery.refetch();
						void definitionOptionsQuery.refetch();
					}}
				/>
			</DrawerBody>
		);
	} else {
		body = (
			<CatalogPracticeForm
				mode="create"
				cancel={<LevelCancel />}
				groups={catalogQuery.data.groups.map(({ slug, name }) => ({ slug, name }))}
				isPending={createPractice.isPending}
				definitionOptions={definitionOptionsQuery.data}
				onSubmit={({ slug, kind, groupSlug, definition }) =>
					createPractice.mutate({ body: { slug, kind, groupSlug, definition, position: null } })
				}
			/>
		);
	}

	return (
		<CatalogFormLevel kind="practice-new" nested={nested} path={path}>
			{body}
		</CatalogFormLevel>
	);
}
