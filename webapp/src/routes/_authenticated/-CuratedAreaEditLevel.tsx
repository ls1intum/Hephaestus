import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminDeleteCuratedAreaOverrideMutation,
	adminGetCuratedAreaOptions,
	adminGetCuratedAreaQueryKey,
	adminGetCuratedCatalogQueryKey,
	adminKeepCuratedAreaMutation,
	adminUpdateCuratedAreaMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CuratedArea } from "@/api/types.gen";
import { CuratedAreaForm } from "@/components/admin/curated-catalog/CuratedAreaForm";
import { CuratedFormLevel } from "@/components/admin/curated-catalog/CuratedFormLevel";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { LevelCancel } from "@/components/core/detail-drawer/LevelCancel";
import { DrawerBody } from "@/components/ui/drawer";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export interface CuratedAreaEditLevelProps {
	areaSlug: string;
	nested?: boolean;
	/** Leaves the level once the group is saved. */
	onDone: () => void;
}

export function CuratedAreaEditLevel({ areaSlug, nested, onDone }: CuratedAreaEditLevelProps) {
	const areaQuery = useQuery({ ...adminGetCuratedAreaOptions({ path: { slug: areaSlug } }) });

	return (
		<CuratedFormLevel kind="area-edit" nested={nested}>
			{areaQuery.isPending ? (
				<DrawerBody>
					<PracticeDefinitionSkeleton />
				</DrawerBody>
			) : areaQuery.isError ? (
				<DrawerBody>
					<QueryErrorAlert
						error={areaQuery.error}
						title="Couldn't load the group"
						onRetry={() => areaQuery.refetch()}
					/>
				</DrawerBody>
			) : (
				<LoadedCuratedAreaEditor
					key={areaSlug}
					areaSlug={areaSlug}
					initialArea={areaQuery.data}
					onDone={onDone}
				/>
			)}
		</CuratedFormLevel>
	);
}

interface LoadedCuratedAreaEditorProps {
	areaSlug: string;
	initialArea: CuratedArea;
	onDone: () => void;
}

function LoadedCuratedAreaEditor({ areaSlug, initialArea, onDone }: LoadedCuratedAreaEditorProps) {
	const queryClient = useQueryClient();
	const [baseArea, setBaseArea] = useState(initialArea);
	const [conflict, setConflict] = useState(false);
	const [formGeneration, setFormGeneration] = useState(0);
	const detailOptions = adminGetCuratedAreaOptions({ path: { slug: areaSlug } });
	const detailQueryKey = adminGetCuratedAreaQueryKey({ path: { slug: areaSlug } });

	const invalidateCatalog = () =>
		void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });

	const updateArea = useMutation({
		...adminUpdateCuratedAreaMutation(),
		onSuccess: (updated: CuratedArea) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			toast.success("Group updated");
			onDone();
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				setConflict(true);
				return;
			}
			toast.error("Couldn't update the group", { description: problemDetailOf(error) });
		},
	});
	const deleteOverride = useMutation({
		...adminDeleteCuratedAreaOverrideMutation(),
		onSuccess: (updated: CuratedArea) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			setBaseArea(updated);
			setFormGeneration((generation) => generation + 1);
			setConflict(false);
			toast.success(
				baseArea.status.state === "UPDATE_WAITING"
					? "Hephaestus update applied"
					: "Hephaestus default restored",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the group to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				invalidateCatalog();
				return;
			}
			toast.error("Couldn't apply the default", { description: problemDetailOf(error) });
		},
	});
	const keepCurrentDefinition = useMutation({
		...adminKeepCuratedAreaMutation(),
		onSuccess: (updated: CuratedArea) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			setBaseArea(updated);
			setConflict(false);
			toast.success(
				baseArea.status.state === "NO_LONGER_SHIPPED"
					? "Saved group is now custom"
					: "Saved version kept",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the group to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				invalidateCatalog();
				return;
			}
			toast.error("Couldn't keep the saved version", { description: problemDetailOf(error) });
		},
	});

	return (
		<CuratedAreaForm
			key={`${areaSlug}-${formGeneration}`}
			mode="edit"
			cancel={<LevelCancel />}
			initialData={{
				slug: baseArea.slug,
				...baseArea.definition,
				description: baseArea.definition.description ?? undefined,
				icon: baseArea.definition.icon ?? undefined,
				color: baseArea.definition.color ?? undefined,
				status: baseArea.status,
				shipped: baseArea.shipped,
			}}
			isPending={updateArea.isPending}
			isResetPending={deleteOverride.isPending}
			isKeepPending={keepCurrentDefinition.isPending}
			conflict={conflict}
			onContinueWithDraft={async () => {
				try {
					await queryClient.invalidateQueries({
						queryKey: detailQueryKey,
						exact: true,
						refetchType: "none",
					});
					const latest: CuratedArea = await queryClient.fetchQuery(detailOptions);
					setBaseArea(latest);
					setConflict(false);
				} catch (error) {
					toast.error("Couldn't load the current version", {
						description: problemDetailOf(error),
					});
				}
			}}
			onUseHephaestusVersion={() => {
				setConflict(false);
				deleteOverride.mutate({
					path: { slug: areaSlug },
					headers: { "If-Match": `"${baseArea.status.etag}"` },
				});
			}}
			onKeepCurrentDefinition={() => {
				setConflict(false);
				keepCurrentDefinition.mutate({
					path: { slug: areaSlug },
					headers: { "If-Match": `"${baseArea.status.etag}"` },
				});
			}}
			onSubmit={({ slug: _slug, ...definition }) => {
				setConflict(false);
				updateArea.mutate({
					path: { slug: areaSlug },
					headers: { "If-Match": `"${baseArea.status.etag}"` },
					body: definition,
				});
			}}
		/>
	);
}
