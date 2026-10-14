import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { toast } from "sonner";

import {
	acknowledgeCatalogGroupMutation,
	getCatalogGroupOptions,
	getCatalogGroupQueryKey,
	getInstanceCatalogQueryKey,
	resetCatalogGroupMutation,
	updateCatalogGroupMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CatalogGroup } from "@/api/types.gen";
import { CatalogFormLevel } from "@/components/admin/catalog/CatalogFormLevel";
import { CatalogGroupForm } from "@/components/admin/catalog/CatalogGroupForm";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { DrawerBody } from "@/components/ui/drawer";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export interface CatalogGroupEditLevelProps {
	groupSlug: string;
	nested?: boolean;
	/** Where the level sits, from the host's `levelPathAt`. */
	path: LevelPath;
	onDone: () => void;
}

export function CatalogGroupEditLevel({
	groupSlug,
	nested,
	path,
	onDone,
}: CatalogGroupEditLevelProps) {
	const groupQuery = useQuery({ ...getCatalogGroupOptions({ path: { slug: groupSlug } }) });

	let body: ReactNode;
	if (groupQuery.isPending) {
		body = (
			<DrawerBody>
				<PracticeDefinitionSkeleton />
			</DrawerBody>
		);
	} else if (groupQuery.isError) {
		body = (
			<DrawerBody>
				<QueryErrorAlert
					error={groupQuery.error}
					title="We could not load the group"
					onRetry={() => {
						void groupQuery.refetch();
					}}
				/>
			</DrawerBody>
		);
	} else {
		body = (
			<LoadedCatalogGroupEditor
				key={groupSlug}
				groupSlug={groupSlug}
				initialGroup={groupQuery.data}
				onDone={onDone}
			/>
		);
	}

	return (
		<CatalogFormLevel kind="group-edit" nested={nested} path={path}>
			{body}
		</CatalogFormLevel>
	);
}

interface LoadedCatalogGroupEditorProps {
	groupSlug: string;
	initialGroup: CatalogGroup;
	onDone: () => void;
}

function LoadedCatalogGroupEditor({
	groupSlug,
	initialGroup,
	onDone,
}: LoadedCatalogGroupEditorProps) {
	const queryClient = useQueryClient();
	const [baseGroup, setBaseGroup] = useState(initialGroup);
	const [conflict, setConflict] = useState(false);
	const [formGeneration, setFormGeneration] = useState(0);
	const detailOptions = getCatalogGroupOptions({ path: { slug: groupSlug } });
	const detailQueryKey = getCatalogGroupQueryKey({ path: { slug: groupSlug } });

	const continueWithDraft = async () => {
		try {
			await queryClient.invalidateQueries({
				queryKey: detailQueryKey,
				exact: true,
				refetchType: "none",
			});
			const latest: CatalogGroup = await queryClient.query(detailOptions);
			setBaseGroup(latest);
			setConflict(false);
		} catch (error) {
			toast.error("We could not load the current version", {
				description: problemDetailOf(error),
			});
		}
	};

	const invalidateCatalog = () => {
		void queryClient.invalidateQueries({ queryKey: getInstanceCatalogQueryKey() });
	};

	const updateGroup = useMutation({
		...updateCatalogGroupMutation(),
		onSuccess: (updated: CatalogGroup) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			toast.success("Group updated");
			onDone();
		},
		onError: (error) => {
			if (problemStatusOf(error) === 409) {
				setConflict(true);
				return;
			}
			toast.error("We could not update the group", { description: problemDetailOf(error) });
		},
	});
	const resetGroup = useMutation({
		...resetCatalogGroupMutation(),
		onSuccess: (updated: CatalogGroup) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			setBaseGroup(updated);
			setFormGeneration((generation) => generation + 1);
			setConflict(false);
			toast.success(
				baseGroup.state === "UPDATE_WAITING"
					? "Hephaestus update applied"
					: "Hephaestus default restored",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 409) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the group to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				invalidateCatalog();
				return;
			}
			toast.error("We could not apply the Hephaestus version", {
				description: problemDetailOf(error),
			});
		},
	});
	const keepCurrentDefinition = useMutation({
		...acknowledgeCatalogGroupMutation(),
		onSuccess: (updated: CatalogGroup) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			setBaseGroup(updated);
			setConflict(false);
			toast.success(
				baseGroup.state === "NO_LONGER_SHIPPED"
					? "Saved group is now custom"
					: "Saved version kept",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 409) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the group to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				invalidateCatalog();
				return;
			}
			toast.error("We could not keep the saved version", { description: problemDetailOf(error) });
		},
	});

	return (
		<CatalogGroupForm
			key={`${groupSlug}-${formGeneration}`}
			mode="edit"
			cancel={<LevelCancel />}
			group={baseGroup}
			isPending={updateGroup.isPending}
			isResetPending={resetGroup.isPending}
			isKeepPending={keepCurrentDefinition.isPending}
			conflict={conflict}
			onContinueWithDraft={() => {
				void continueWithDraft();
			}}
			onUseHephaestusVersion={() => {
				setConflict(false);
				resetGroup.mutate({
					path: { slug: groupSlug },
					headers: { "If-Match": `"${baseGroup.etag}"` },
				});
			}}
			onKeepCurrentDefinition={() => {
				setConflict(false);
				keepCurrentDefinition.mutate({
					path: { slug: groupSlug },
					headers: { "If-Match": `"${baseGroup.etag}"` },
				});
			}}
			onSubmit={(group) => {
				setConflict(false);
				updateGroup.mutate({
					path: { slug: groupSlug },
					headers: { "If-Match": `"${baseGroup.etag}"` },
					body: { ...group, position: null },
				});
			}}
		/>
	);
}
