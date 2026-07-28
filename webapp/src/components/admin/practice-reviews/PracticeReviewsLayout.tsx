import { Link, useMatchRoute, useParams, useSearch } from "@tanstack/react-router";
import type { ReactNode } from "react";
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
		label: "Review activity",
		title: "Practice reviews",
		description: "See when reviews ran and what they produced.",
	},
	{
		id: "findings",
		to: "/w/$workspaceSlug/admin/practices/reviews/findings",
		label: "Findings",
		title: "Findings",
		description: "Explore strengths and improvements, with evidence from reviewed work.",
	},
	{
		id: "delivery",
		to: "/w/$workspaceSlug/admin/practices/reviews/delivery",
		label: "Delivery",
		title: "Feedback delivery",
		description: "See each message Hephaestus prepared and whether it reached its recipient.",
	},
] as const;

export type PracticeReviewSection = (typeof VIEWS)[number]["id"];

export interface PracticeReviewsHeaderProps {
	workspaceSlug: string;
	activeSection: PracticeReviewSection;
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
		artifactType:
			"artifactType" in params && typeof params.artifactType === "string"
				? reviewArtifactTypeFromSlug(params.artifactType)
				: "artifactType" in search
					? search.artifactType
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
	const findingsActive = Boolean(
		matchRoute({
			to: "/w/$workspaceSlug/admin/practices/reviews/findings",
			fuzzy: true,
		}),
	);
	const activeId = deliveryActive ? "delivery" : findingsActive ? "findings" : "reviews";

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6">
			<PracticeReviewsHeader workspaceSlug={workspaceSlug} activeSection={activeId} scope={scope} />
			{children}
		</div>
	);
}

export function PracticeReviewsHeader({
	workspaceSlug,
	activeSection,
	scope = {},
}: PracticeReviewsHeaderProps) {
	const activeView = VIEWS.find((view) => view.id === activeSection) ?? VIEWS[0];
	return (
		<header className="space-y-4">
			<div className="space-y-1">
				<h1 className="text-3xl font-bold tracking-tight">{activeView.title}</h1>
				<p className="max-w-2xl text-muted-foreground">{activeView.description}</p>
			</div>
			<nav
				aria-label="Practice review sections"
				className="grid grid-cols-[1.4fr_1fr_1fr] gap-1 rounded-lg bg-muted p-1 sm:grid-cols-3"
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
							"min-w-0 px-2 text-foreground aria-[current=page]:bg-background aria-[current=page]:font-semibold aria-[current=page]:shadow-sm aria-[current=page]:hover:bg-background",
						)}
					>
						{label}
					</Link>
				))}
			</nav>
		</header>
	);
}
