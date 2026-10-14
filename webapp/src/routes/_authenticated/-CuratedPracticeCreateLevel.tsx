import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { toast } from "sonner";

import {
	adminCreateCuratedPracticeMutation,
	adminGetCuratedCatalogOptions,
	adminGetCuratedCatalogQueryKey,
	adminGetPracticeDefinitionOptionsOptions,
} from "@/api/@tanstack/react-query.gen";
import { CuratedFormLevel } from "@/components/admin/curated-catalog/CuratedFormLevel";
import {
	CuratedPracticeForm,
	type CuratedPracticeFormValue,
} from "@/components/admin/curated-catalog/CuratedPracticeForm";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert, queryErrorIsRetryable } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { DrawerBody } from "@/components/ui/drawer";
import { problemDetailOf } from "@/lib/problem-detail";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

export interface CuratedPracticeCreateLevelProps {
	nested?: boolean;
	/** Where the level sits, from the host's `levelPathAt`. */
	path: LevelPath;
	onDone: () => void;
}

export function CuratedPracticeCreateLevel({
	nested,
	path,
	onDone,
}: CuratedPracticeCreateLevelProps) {
	const queryClient = useQueryClient();
	const catalogOptions = adminGetCuratedCatalogOptions();
	const definitionOptionsOptions = adminGetPracticeDefinitionOptionsOptions();
	const catalogQuery = useQuery(catalogOptions);
	const definitionOptionsQuery = useQuery(definitionOptionsOptions);
	useQueryReadRefusal(catalogOptions.queryKey, catalogQuery.isError, catalogQuery.error);
	useQueryReadRefusal(
		definitionOptionsOptions.queryKey,
		definitionOptionsQuery.isError,
		definitionOptionsQuery.error,
	);
	const failed = [catalogQuery, definitionOptionsQuery].filter((read) => read.isError);
	// A refusal hides the editor even over cached data. Any other failure leaves a loaded draft open.
	const failure = failed.find((read) => !queryErrorIsRetryable(read.error)) ?? failed[0];
	const refused = failure !== undefined && !queryErrorIsRetryable(failure.error);
	const retryFailed = () => {
		for (const read of failed) {
			void read.refetch();
		}
	};
	const createPractice = useMutation({
		...adminCreateCuratedPracticeMutation(),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success("Practice created");
			onDone();
		},
		onError: (error) =>
			toast.error("We could not create the practice", { description: problemDetailOf(error) }),
	});

	let body: ReactNode;
	if (!refused && catalogQuery.data !== undefined && definitionOptionsQuery.data !== undefined) {
		body = (
			<CuratedPracticeForm
				mode="create"
				cancel={<LevelCancel />}
				groups={catalogQuery.data.groups.map((group) => ({
					slug: group.slug,
					name: group.definition.name,
				}))}
				isPending={createPractice.isPending}
				definitionOptions={definitionOptionsQuery.data}
				refreshAlert={
					failure === undefined ? undefined : (
						<QueryErrorAlert
							error={failure.error}
							title="We could not refresh the practice editor"
							pending={failed.some((read) => read.isFetching)}
							onRetry={retryFailed}
						/>
					)
				}
				onSubmit={({
					slug,
					definitionChanges: _definitionChanges,
					...definition
				}: CuratedPracticeFormValue) => createPractice.mutate({ body: { slug, definition } })}
			/>
		);
	} else if (failure === undefined) {
		body = (
			<DrawerBody>
				<PracticeDefinitionSkeleton />
			</DrawerBody>
		);
	} else {
		body = (
			<DrawerBody>
				<QueryErrorAlert
					error={failure.error}
					title="We could not load the practice editor"
					onRetry={retryFailed}
				/>
			</DrawerBody>
		);
	}

	return (
		<CuratedFormLevel kind="practice-new" nested={nested} path={path}>
			{body}
		</CuratedFormLevel>
	);
}
