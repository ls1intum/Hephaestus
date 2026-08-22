import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
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
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/areas/$areaSlug")({
	head: instanceAdminHead("Edit area"),
	component: EditCuratedAreaPage,
});

function EditCuratedAreaPage() {
	const { areaSlug } = Route.useParams();
	const areaQuery = useQuery({ ...adminGetCuratedAreaOptions({ path: { slug: areaSlug } }) });

	if (areaQuery.isPending) {
		return (
			<PageLayout>
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PageLayout>
		);
	}
	if (areaQuery.isError) {
		return (
			<PageLayout>
				<QueryErrorAlert
					error={areaQuery.error}
					title="Couldn't load the area"
					onRetry={() => void areaQuery.refetch()}
				/>
			</PageLayout>
		);
	}

	return (
		<LoadedEditCuratedAreaPage key={areaSlug} areaSlug={areaSlug} initialArea={areaQuery.data} />
	);
}

interface LoadedEditCuratedAreaPageProps {
	areaSlug: string;
	initialArea: CuratedArea;
}

function LoadedEditCuratedAreaPage({ areaSlug, initialArea }: LoadedEditCuratedAreaPageProps) {
	const navigate = useNavigate({ from: Route.fullPath });
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
			toast.success("Area updated");
			void navigate({ to: "/admin/catalog" });
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				setConflict(true);
				return;
			}
			toast.error("Couldn't update the area", { description: problemDetailOf(error) });
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
					"The catalog changed before this action was saved. Reopen the area to see the latest version.",
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
					? "Saved area is now custom"
					: "Saved version kept",
			);
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				toast.error(
					"The catalog changed before this action was saved. Reopen the area to see the latest version.",
				);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				invalidateCatalog();
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
			const latest: CuratedArea = await queryClient.fetchQuery(detailOptions);
			setBaseArea(latest);
			setConflict(false);
		} catch (error) {
			toast.error("Couldn't load the current version", { description: problemDetailOf(error) });
		}
	};

	return (
		<CuratedAreaForm
			key={`${areaSlug}-${formGeneration}`}
			mode="edit"
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
			onContinueWithDraft={() => void continueWithDraft()}
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
