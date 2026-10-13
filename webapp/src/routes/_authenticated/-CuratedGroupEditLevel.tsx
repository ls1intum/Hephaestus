import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { toast } from "sonner";

import {
	adminDeleteCuratedGroupOverrideMutation,
	adminGetCuratedCatalogQueryKey,
	adminGetCuratedGroupOptions,
	adminGetCuratedGroupQueryKey,
	adminKeepCuratedGroupMutation,
	adminUpdateCuratedGroupMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CuratedGroup } from "@/api/types.gen";
import { CuratedFormLevel } from "@/components/admin/curated-catalog/CuratedFormLevel";
import { CuratedGroupForm } from "@/components/admin/curated-catalog/CuratedGroupForm";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert, queryErrorIsRetryable } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { DrawerBody } from "@/components/ui/drawer";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

export interface CuratedGroupEditLevelProps {
	groupSlug: string;
	nested?: boolean;
	/** Where the level sits, from the host's `levelPathAt`. */
	path: LevelPath;
	onDone: () => void;
}

export function CuratedGroupEditLevel({
	groupSlug,
	nested,
	path,
	onDone,
}: CuratedGroupEditLevelProps) {
	const groupOptions = adminGetCuratedGroupOptions({ path: { slug: groupSlug } });
	const groupQuery = useQuery(groupOptions);
	useQueryReadRefusal(groupOptions.queryKey, groupQuery.isError, groupQuery.error);
	const retry = () => {
		void groupQuery.refetch();
	};

	let body: ReactNode;
	// A refusal hides the editor even over cached data. Any other failure leaves a loaded draft open.
	if (
		groupQuery.data !== undefined &&
		(!groupQuery.isError || queryErrorIsRetryable(groupQuery.error))
	) {
		body = (
			<LoadedCuratedGroupEditor
				key={groupSlug}
				groupSlug={groupSlug}
				initialGroup={groupQuery.data}
				refreshAlert={
					groupQuery.isError ? (
						<QueryErrorAlert
							error={groupQuery.error}
							title="We could not refresh the group"
							pending={groupQuery.isFetching}
							onRetry={retry}
						/>
					) : undefined
				}
				onDone={onDone}
			/>
		);
	} else if (groupQuery.isError) {
		body = (
			<DrawerBody>
				<QueryErrorAlert
					error={groupQuery.error}
					title="We could not load the group"
					onRetry={retry}
				/>
			</DrawerBody>
		);
	} else {
		body = (
			<DrawerBody>
				<PracticeDefinitionSkeleton />
			</DrawerBody>
		);
	}

	return (
		<CuratedFormLevel kind="group-edit" nested={nested} path={path}>
			{body}
		</CuratedFormLevel>
	);
}

interface LoadedCuratedGroupEditorProps {
	groupSlug: string;
	initialGroup: CuratedGroup;
	refreshAlert: ReactNode;
	onDone: () => void;
}

function LoadedCuratedGroupEditor({
	groupSlug,
	initialGroup,
	refreshAlert,
	onDone,
}: LoadedCuratedGroupEditorProps) {
	const queryClient = useQueryClient();
	const [baseGroup, setBaseGroup] = useState(initialGroup);
	const [conflict, setConflict] = useState(false);
	const [formGeneration, setFormGeneration] = useState(0);
	const detailOptions = adminGetCuratedGroupOptions({ path: { slug: groupSlug } });
	const detailQueryKey = adminGetCuratedGroupQueryKey({ path: { slug: groupSlug } });

	const continueWithDraft = async () => {
		try {
			await queryClient.invalidateQueries({
				queryKey: detailQueryKey,
				exact: true,
				refetchType: "none",
			});
			const latest: CuratedGroup = await queryClient.query(detailOptions);
			setBaseGroup(latest);
			setConflict(false);
		} catch (error) {
			toast.error("We could not load the current version", {
				description: problemDetailOf(error),
			});
		}
	};

	const invalidateCatalog = () => {
		void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
	};

	const updateGroup = useMutation({
		...adminUpdateCuratedGroupMutation(),
		onSuccess: (updated: CuratedGroup) => {
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
			toast.error("We could not update the group", { description: problemDetailOf(error) });
		},
	});
	const deleteOverride = useMutation({
		...adminDeleteCuratedGroupOverrideMutation(),
		onSuccess: (updated: CuratedGroup) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			setBaseGroup(updated);
			setFormGeneration((generation) => generation + 1);
			setConflict(false);
			toast.success(
				baseGroup.status.state === "UPDATE_WAITING"
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
			toast.error("We could not apply the Hephaestus version", {
				description: problemDetailOf(error),
			});
		},
	});
	const keepCurrentDefinition = useMutation({
		...adminKeepCuratedGroupMutation(),
		onSuccess: (updated: CuratedGroup) => {
			queryClient.setQueryData(detailQueryKey, updated);
			invalidateCatalog();
			setBaseGroup(updated);
			setConflict(false);
			toast.success(
				baseGroup.status.state === "NO_LONGER_SHIPPED"
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
			toast.error("We could not keep the saved version", { description: problemDetailOf(error) });
		},
	});

	return (
		<CuratedGroupForm
			key={`${groupSlug}-${formGeneration}`}
			mode="edit"
			cancel={<LevelCancel />}
			initialData={{
				slug: baseGroup.slug,
				...baseGroup.definition,
				description: baseGroup.definition.description ?? undefined,
				icon: baseGroup.definition.icon ?? undefined,
				color: baseGroup.definition.color ?? undefined,
				status: baseGroup.status,
				shipped: baseGroup.shipped,
			}}
			isPending={updateGroup.isPending}
			isResetPending={deleteOverride.isPending}
			isKeepPending={keepCurrentDefinition.isPending}
			refreshAlert={refreshAlert}
			conflict={conflict}
			onContinueWithDraft={() => {
				void continueWithDraft();
			}}
			onUseHephaestusVersion={() => {
				setConflict(false);
				deleteOverride.mutate({
					path: { slug: groupSlug },
					headers: { "If-Match": `"${baseGroup.status.etag}"` },
				});
			}}
			onKeepCurrentDefinition={() => {
				setConflict(false);
				keepCurrentDefinition.mutate({
					path: { slug: groupSlug },
					headers: { "If-Match": `"${baseGroup.status.etag}"` },
				});
			}}
			onSubmit={({ slug: _slug, ...definition }) => {
				setConflict(false);
				updateGroup.mutate({
					path: { slug: groupSlug },
					headers: { "If-Match": `"${baseGroup.status.etag}"` },
					body: definition,
				});
			}}
		/>
	);
}
