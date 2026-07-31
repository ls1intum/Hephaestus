import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminDeleteCuratedPracticeOverrideMutation,
	adminGetCuratedPracticeOptions,
	adminGetCuratedPracticeQueryKey,
	adminListCuratedPracticeAreasOptions,
	adminListCuratedPracticesQueryKey,
	adminUpdateCuratedPracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CuratedPracticeArea, CuratedPracticeDetail } from "@/api/types.gen";
import {
	CuratedPracticeForm,
	type CuratedPracticeFormValue,
} from "@/components/admin/curated-practices/CuratedPracticeForm";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/catalog/$practiceSlug")({
	head: instanceAdminHead("Edit curated practice"),
	component: EditCuratedPracticePage,
});

function EditCuratedPracticePage() {
	const { practiceSlug } = Route.useParams();
	const practiceQuery = useQuery({
		...adminGetCuratedPracticeOptions({ path: { slug: practiceSlug } }),
	});
	const areasQuery = useQuery({ ...adminListCuratedPracticeAreasOptions() });

	if (practiceQuery.isPending || areasQuery.isPending) {
		return (
			<PageLayout>
				<div className="flex h-64 items-center justify-center">
					<Spinner className="size-8" />
				</div>
			</PageLayout>
		);
	}
	if (practiceQuery.isError || areasQuery.isError) {
		return (
			<PageLayout>
				<QueryErrorAlert
					error={practiceQuery.error ?? areasQuery.error}
					title="Couldn't load the curated practice"
					onRetry={() => {
						practiceQuery.refetch();
						areasQuery.refetch();
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
			areas={areasQuery.data}
		/>
	);
}

interface LoadedEditCuratedPracticePageProps {
	practiceSlug: string;
	initialPractice: CuratedPracticeDetail;
	areas: CuratedPracticeArea[];
}

function LoadedEditCuratedPracticePage({
	practiceSlug,
	initialPractice,
	areas,
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
			void queryClient.invalidateQueries({ queryKey: adminListCuratedPracticesQueryKey() });
			toast.success("Curated practice updated");
			navigate({ to: "/admin/catalog" });
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				setConflict(true);
				return;
			}
			toast.error("Couldn't update the curated practice", { description: problemDetailOf(error) });
		},
	});
	const deleteOverride = useMutation({
		...adminDeleteCuratedPracticeOverrideMutation(),
		onSuccess: (updated) => {
			queryClient.setQueryData(detailQueryKey, updated);
			void queryClient.invalidateQueries({ queryKey: detailQueryKey });
			void queryClient.invalidateQueries({ queryKey: adminListCuratedPracticesQueryKey() });
			setBasePractice(updated);
			setFormGeneration((generation) => generation + 1);
			setConflict(false);
			toast.success("Now using the Hephaestus version");
		},
		onError: (error) => {
			if (problemStatusOf(error) === 412) {
				setConflict(true);
				void queryClient.invalidateQueries({ queryKey: detailQueryKey });
				void queryClient.invalidateQueries({ queryKey: adminListCuratedPracticesQueryKey() });
				return;
			}
			toast.error("Couldn't use the Hephaestus version", { description: problemDetailOf(error) });
		},
	});

	const submit = ({ slug: _slug, ...value }: CuratedPracticeFormValue) => {
		setConflict(false);
		updatePractice.mutate({
			path: { slug: practiceSlug },
			headers: { "If-Match": `"v${basePractice.version}"` },
			body: value,
		});
	};
	const continueWithDraft = async () => {
		try {
			await queryClient.invalidateQueries({
				queryKey: detailQueryKey,
				exact: true,
				refetchType: "none",
			});
			const latest = await queryClient.fetchQuery(detailOptions);
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
			initialData={basePractice}
			areas={areas}
			isPending={updatePractice.isPending}
			isResetPending={deleteOverride.isPending}
			conflict={conflict}
			onContinueWithDraft={continueWithDraft}
			onUseBundledVersion={() => {
				setConflict(false);
				deleteOverride.mutate({
					path: { slug: practiceSlug },
					headers: { "If-Match": `"v${basePractice.version}"` },
				});
			}}
			onSubmit={submit}
		/>
	);
}
