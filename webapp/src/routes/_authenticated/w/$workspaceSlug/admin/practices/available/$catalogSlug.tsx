import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { BookOpenCheck } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import {
	adoptPracticeMutation,
	getPracticeDefinitionOptionsOptions,
	listAdoptablePracticesQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	previewPracticeAdoptionOptions,
} from "@/api/@tanstack/react-query.gen";
import { PracticeAdoptionReview } from "@/components/admin/practice-adoption/PracticeAdoptionReview";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Spinner } from "@/components/ui/spinner";
import { practiceCatalogStructureScope } from "@/hooks/practice-catalog-cache";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/available/$catalogSlug",
)({
	head: workspaceAdminHead("Review available practice"),
	component: PracticeAdoptionRoute,
});

function PracticeAdoptionRoute() {
	const { workspaceSlug, catalogSlug } = Route.useParams();
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const [stale, setStale] = useState(false);
	const staleHeadingRef = useRef<HTMLHeadingElement>(null);
	const previewQuery = useQuery({
		...previewPracticeAdoptionOptions({ path: { workspaceSlug, slug: catalogSlug } }),
	});
	const definitionOptionsQuery = useQuery({
		...getPracticeDefinitionOptionsOptions({ path: { workspaceSlug } }),
	});
	const adopt = useMutation({
		...adoptPracticeMutation(),
		scope: practiceCatalogStructureScope(workspaceSlug),
	});
	useEffect(() => {
		if (stale) staleHeadingRef.current?.focus();
	}, [stale]);

	const adoptReviewedPractice = async () => {
		if (!previewQuery.data) return;
		setStale(false);
		try {
			const practice = await adopt.mutateAsync({
				path: { workspaceSlug, slug: catalogSlug },
				headers: { "If-Match": previewQuery.data.etag },
			});
			await Promise.all([
				queryClient.invalidateQueries({
					queryKey: listAdoptablePracticesQueryKey({ path: { workspaceSlug } }),
				}),
				queryClient.invalidateQueries({
					queryKey: listPracticesQueryKey({ path: { workspaceSlug } }),
				}),
				queryClient.invalidateQueries({ queryKey: listAreasQueryKey({ path: { workspaceSlug } }) }),
			]);
			toast.success("Practice adopted with Review before sending");
			await navigate({
				to: "/w/$workspaceSlug/admin/practices/$practiceSlug",
				params: { workspaceSlug, practiceSlug: practice.slug },
			});
		} catch (error) {
			const status = problemStatusOf(error);
			if (status === 412) {
				const refreshed = await previewQuery.refetch();
				if (refreshed.isSuccess) {
					setStale(true);
				} else {
					toast.error("The adoption preview changed but couldn't be refreshed");
				}
				return;
			}
			if (status === 409) {
				toast.info("This practice is already in the workspace");
				await navigate({
					to: "/w/$workspaceSlug/admin/practices/$practiceSlug",
					params: { workspaceSlug, practiceSlug: catalogSlug },
				});
				return;
			}
			toast.error("Couldn't adopt the practice");
		}
	};

	return (
		<PageLayout>
			<PageHeader
				icon={<BookOpenCheck />}
				title={previewQuery.data?.definition.name ?? "Review practice"}
				description="Review the definition and workspace changes before adopting."
			/>
			{stale && (
				<Alert variant="warning" className="mb-6">
					<AlertTitle>
						<h2 ref={staleHeadingRef} id="adoption-preview-changed" tabIndex={-1}>
							The adoption preview changed
						</h2>
					</AlertTitle>
					<AlertDescription>
						The latest definition or workspace outcome is now shown. Review it again before
						adopting.
					</AlertDescription>
				</Alert>
			)}
			{previewQuery.isPending || definitionOptionsQuery.isPending ? (
				<div className="flex h-64 items-center justify-center gap-3" role="status">
					<Spinner className="size-8" />
					<span className="sr-only">Loading adoption preview</span>
				</div>
			) : previewQuery.isError || definitionOptionsQuery.isError ? (
				<QueryErrorAlert
					error={previewQuery.error ?? definitionOptionsQuery.error}
					title="Couldn't load the adoption preview"
					onRetry={() => {
						previewQuery.refetch();
						definitionOptionsQuery.refetch();
					}}
				/>
			) : (
				<PracticeAdoptionReview
					workspaceSlug={workspaceSlug}
					preview={previewQuery.data}
					definitionOptions={definitionOptionsQuery.data}
					onAdopt={adoptReviewedPractice}
					isPending={adopt.isPending}
				/>
			)}
		</PageLayout>
	);
}
