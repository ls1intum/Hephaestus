import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ReviewPage } from "@/components/admin/practices/review/ReviewPage";
import {
	DEFAULT_REVIEW_SECTION,
	reviewSearchSchema,
} from "@/components/admin/practices/review/review-sections";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/review")({
	head: workspaceAdminHead("Review"),
	validateSearch: reviewSearchSchema,
	component: ReviewRoute,
});

function ReviewRoute() {
	const { workspaceSlug } = Route.useParams();
	const { section, overrides } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	return (
		<ReviewPage
			workspaceSlug={workspaceSlug}
			section={section ?? DEFAULT_REVIEW_SECTION}
			// The default section is left out of the URL rather than written into it, so the sidebar's
			// link and a tab click on the first section produce the same address.
			onSectionChange={(next) =>
				navigate({
					search: (previous) => ({
						...previous,
						section: next === DEFAULT_REVIEW_SECTION ? undefined : next,
					}),
				})
			}
			overridesOnly={overrides === true}
			onOverridesOnlyChange={(next) =>
				navigate({ search: (previous) => ({ ...previous, overrides: next ? true : undefined }) })
			}
		/>
	);
}
