import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminDeleteCuratedPracticeOverrideMutation,
	adminGetCuratedCatalogOptions,
	adminGetCuratedCatalogQueryKey,
	adminGetCuratedPracticeOptions,
	adminGetCuratedPracticeQueryKey,
	adminGetPracticeDefinitionOptionsOptions,
	adminKeepCuratedPracticeMutation,
	adminUpdateCuratedPracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CuratedArea, CuratedPractice, PracticeDefinitionOptions } from "@/api/types.gen";
import {
	CuratedPracticeForm,
	type CuratedPracticeFormValue,
} from "@/components/admin/curated-catalog/CuratedPracticeForm";
import { soleBinding } from "@/components/admin/practice-catalog/bindings";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/practices/$practiceSlug")({
	head: instanceAdminHead("Edit practice"),
	component: EditCuratedPracticePage,
});

function EditCuratedPracticePage() {
	const { practiceSlug } = Route.useParams();
	const practiceQuery = useQuery({
		...adminGetCuratedPracticeOptions({ path: { slug: practiceSlug } }),
	});
	const catalogQuery = useQuery({ ...adminGetCuratedCatalogOptions() });
	const definitionOptionsQuery = useQuery({ ...adminGetPracticeDefinitionOptionsOptions() });

	if (practiceQuery.isPending || catalogQuery.isPending || definitionOptionsQuery.isPending) {
		return (
			<PageLayout>
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PageLayout>
		);
	}
	if (practiceQuery.isError || catalogQuery.isError || definitionOptionsQuery.isError) {
		return (
			<PageLayout>
				<QueryErrorAlert
					error={practiceQuery.error ?? catalogQuery.error ?? definitionOptionsQuery.error}
					title="Couldn't load the practice"
					onRetry={() => {
						void practiceQuery.refetch();
						void catalogQuery.refetch();
						void definitionOptionsQuery.refetch();
					}}
				/>
			</PageLayout>
		);
	}

	return (
		<LoadedEditCuratedPracticePage
			key={practiceSlug}
			practiceSlug={practiceSlug}
			initialPractice={practiceQuery.data}
			areas={catalogQuery.data.areas}
			definitionOptions={definitionOptionsQuery.data}
		/>
	);
}

interface LoadedEditCuratedPracticePageProps {
	practiceSlug: string;
	initialPractice: CuratedPractice;
	areas: CuratedArea[];
	definitionOptions: PracticeDefinitionOptions;
}

function LoadedEditCuratedPracticePage({
	practiceSlug,
	initialPractice,
	areas,
	definitionOptions,
}: LoadedEditCuratedPracticePageProps) {
	const navigate = useNavigate({ from: Route.fullPath });
	const queryClient = useQueryClient();
	const [basePractice, setBasePractice] = useState(initialPractice);
	const [conflict, setConflict] = useState(false);
	const [formGeneration, setFormGeneration] = useState(0);
	const detailOptions = adminGetCuratedPracticeOptions({ path: { slug: practiceSlug } });
	const detailQueryKey = adminGetCuratedPracticeQueryKey({ path: { slug: practiceSlug } });
	const updatePractice = useMutation({
		...adminUpdateCuratedPracticeMutation(),
		onSuccess: (updated) => {
			queryClient.setQueryData(detailQueryKey, updated);
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			toast.success("Practice updated");
			void navigate({ to: "/admin/catalog" });
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				setConflict(true);
				return;
			}
			toast.error("Couldn't update the practice", { description: problemDetailOf(error) });
		},
	});
	const deleteOverride = useMutation({
		...adminDeleteCuratedPracticeOverrideMutation(),
		onSuccess: (updated: CuratedPractice) => {
			queryClient.setQueryData(detailQueryKey, updated);
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			setBasePractice(updated);
			setFormGeneration((generation) => generation + 1);
			setConflict(false);
			toast.success(
				basePractice.status.state === "UPDATE_WAITING"
					? "Hephaestus update applied"
					: "Hephaestus default restored",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the practice to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
				return;
			}
			toast.error("Couldn't apply the default", { description: problemDetailOf(error) });
		},
	});
	const keepCurrentDefinition = useMutation({
		...adminKeepCuratedPracticeMutation(),
		onSuccess: (updated: CuratedPractice) => {
			queryClient.setQueryData(detailQueryKey, updated);
			void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
			setBasePractice(updated);
			setConflict(false);
			toast.success(
				basePractice.status.state === "NO_LONGER_SHIPPED"
					? "Saved practice is now custom"
					: "Saved version kept",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the practice to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				void queryClient.invalidateQueries({ queryKey: adminGetCuratedCatalogQueryKey() });
				return;
			}
			toast.error("Couldn't keep the saved version", { description: problemDetailOf(error) });
		},
	});

	const continueWithDraft = async () => {
		try {
			await queryClient.invalidateQueries({
				queryKey: detailQueryKey,
				exact: true,
				refetchType: "none",
			});
			const latest: CuratedPractice = await queryClient.fetchQuery(detailOptions);
			setBasePractice(latest);
			setConflict(false);
		} catch (error) {
			toast.error("Couldn't refresh the latest version", { description: problemDetailOf(error) });
		}
	};

	return (
		<CuratedPracticeForm
			key={`${practiceSlug}-${formGeneration}`}
			mode="edit"
			initialData={{
				slug: basePractice.slug,
				...basePractice.definition,
				bindings: [soleBinding(basePractice.definition.bindings)],
				precomputeScript: basePractice.definition.precomputeScript ?? undefined,
				whyItMatters: basePractice.definition.whyItMatters ?? undefined,
				whatGoodLooksLike: basePractice.definition.whatGoodLooksLike ?? undefined,
				areaSlug: basePractice.definition.areaSlug ?? undefined,
				status: basePractice.status,
				shipped: basePractice.shipped,
			}}
			areas={areas.map((area) => ({ slug: area.slug, name: area.definition.name }))}
			definitionOptions={definitionOptions}
			isPending={updatePractice.isPending}
			isResetPending={deleteOverride.isPending}
			isKeepPending={keepCurrentDefinition.isPending}
			conflict={conflict}
			onContinueWithDraft={continueWithDraft}
			onUseHephaestusVersion={() => {
				setConflict(false);
				deleteOverride.mutate({
					path: { slug: practiceSlug },
					headers: { "If-Match": `"${basePractice.status.etag}"` },
				});
			}}
			onKeepCurrentDefinition={() => {
				setConflict(false);
				keepCurrentDefinition.mutate({
					path: { slug: practiceSlug },
					headers: { "If-Match": `"${basePractice.status.etag}"` },
				});
			}}
			onSubmit={({ slug: _slug, ...definition }: CuratedPracticeFormValue) => {
				setConflict(false);
				updatePractice.mutate({
					path: { slug: practiceSlug },
					headers: { "If-Match": `"${basePractice.status.etag}"` },
					body: {
						...definition,
					},
				});
			}}
		/>
	);
}
