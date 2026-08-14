import { Link, useMatchRoute, useParams, useSearch } from "@tanstack/react-router";
import { MessageSquareText, ScanSearch, Workflow } from "lucide-react";
import type { ReactNode } from "react";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { reviewArtifactTypeFromSlug } from "./ReviewArtifact";
import type { ReviewScopeSearch } from "./review-search";

export interface PracticeReviewsLayoutProps {
	workspaceSlug: string;
	children: ReactNode;
}

const VIEWS = [
	{
		id: "reviews",
		to: "/w/$workspaceSlug/admin/practices/reviews",
		label: "Reviews",
		title: "Practice reviews",
		description: "See when reviews ran and what they produced.",
		icon: Workflow,
	},
	{
		id: "observations",
		to: "/w/$workspaceSlug/admin/practices/reviews/observations",
		label: "Observations",
		title: "Observations",
		description: "What the reviews found in the work, with the passages they read it from.",
		icon: ScanSearch,
	},
	{
		id: "delivery",
		to: "/w/$workspaceSlug/admin/practices/reviews/delivery",
		label: "Delivery",
		title: "Feedback delivery",
		// No product name, and no "prepared": nothing is prepared outside the conversation queue, and
		// the operator's question is what became of the feedback, not which service composed it.
		description: "Every piece of feedback a review composed, and what became of it.",
		icon: MessageSquareText,
	},
] as const;

export type PracticeReviewSection = (typeof VIEWS)[number]["id"];

export interface PracticeReviewsHeaderProps {
	workspaceSlug: string;
	activeSection?: PracticeReviewSection;
	scope?: ReviewScopeSearch;
}

export function PracticeReviewsLayout({ workspaceSlug, children }: PracticeReviewsLayoutProps) {
	const matchRoute = useMatchRoute();
	const params = useParams({ strict: false });
	const search = useSearch({ strict: false });
	const artifactId =
		"artifactId" in params
			? Number(params.artifactId)
			: "artifactId" in search
				? search.artifactId
				: undefined;
	const scope = {
		agentJobId:
			"jobId" in params ? params.jobId : "agentJobId" in search ? search.agentJobId : undefined,
		artifactKind:
			"artifactKind" in params && typeof params.artifactKind === "string"
				? reviewArtifactTypeFromSlug(params.artifactKind)
				: "artifactKind" in search
					? search.artifactKind
					: undefined,
		artifactId: Number.isSafeInteger(artifactId) ? artifactId : undefined,
		from: "from" in search ? search.from : undefined,
		to: "to" in search ? search.to : undefined,
	};
	const deliveryActive = Boolean(
		matchRoute({
			to: "/w/$workspaceSlug/admin/practices/reviews/delivery",
			fuzzy: true,
		}),
	);
	const observationsActive = Boolean(
		matchRoute({
			to: "/w/$workspaceSlug/admin/practices/reviews/observations",
			fuzzy: true,
		}),
	);
	const targetActive = Boolean(
		matchRoute({
			to: "/w/$workspaceSlug/admin/practices/reviews/targets/$artifactKind/$artifactId",
			fuzzy: true,
		}),
	);
	const activeId = targetActive
		? undefined
		: deliveryActive
			? "delivery"
			: observationsActive
				? "observations"
				: "reviews";

	return (
		<PageLayout>
			<PracticeReviewsHeader workspaceSlug={workspaceSlug} activeSection={activeId} scope={scope} />
			{children}
		</PageLayout>
	);
}

export function PracticeReviewsHeader({
	workspaceSlug,
	activeSection,
	scope = {},
}: PracticeReviewsHeaderProps) {
	const activeView = VIEWS.find((view) => view.id === activeSection);
	const title = activeView?.title ?? "Reviewed work";
	const description =
		activeView?.description ?? "Everything the reviews have said about one piece of work.";
	const Icon = activeView?.icon ?? ScanSearch;
	return (
		<div className="space-y-4">
			<PageHeader icon={<Icon />} title={title} description={description} />
			<nav
				aria-label="Practice review sections"
				className="grid grid-cols-3 gap-1 rounded-lg bg-muted p-1 sm:inline-grid"
			>
				{VIEWS.map(({ id, to, label }) => (
					<Link
						key={to}
						to={to}
						params={{ workspaceSlug }}
						search={id === "reviews" ? {} : scope}
						aria-current={id === activeSection ? "page" : undefined}
						activeOptions={{ exact: true }}
						className={cn(
							buttonVariants({ variant: "ghost", size: "sm" }),
							"min-w-0 px-2 text-muted-foreground aria-[current=page]:bg-background aria-[current=page]:text-foreground aria-[current=page]:shadow-sm aria-[current=page]:hover:bg-background",
						)}
					>
						{label}
					</Link>
				))}
			</nav>
		</div>
	);
}
