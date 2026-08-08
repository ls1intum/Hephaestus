import {
	CircleDotIcon,
	ExternalLinkIcon,
	GitPullRequestIcon,
	MessagesSquareIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import type { ReviewArtifact as ReviewArtifactData, ReviewRunTarget } from "@/api/types.gen";
import {
	ARTIFACT_KIND,
	artifactKindLabel,
	isKnownArtifactKind,
	type KnownArtifactKind,
} from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";

export type ReviewArtifactDisplay = ReviewArtifactData | ReviewRunTarget;
/**
 * URL-facing spelling of a kind: the wire id carries a dot, which reads badly in a path segment, so
 * the routes keep their own short slug and this map is where the two meet.
 */
const ARTIFACT_KIND_SLUGS = {
	[ARTIFACT_KIND.pullRequest]: "pull-request",
	[ARTIFACT_KIND.issue]: "issue",
	[ARTIFACT_KIND.conversationThread]: "conversation",
} as const satisfies Record<KnownArtifactKind, string>;

export type ReviewArtifactTypeSlug = (typeof ARTIFACT_KIND_SLUGS)[keyof typeof ARTIFACT_KIND_SLUGS];

export function reviewArtifactTypeSlug(kind: string): ReviewArtifactTypeSlug | undefined {
	return isKnownArtifactKind(kind) ? ARTIFACT_KIND_SLUGS[kind] : undefined;
}

export function reviewArtifactTypeFromSlug(slug: string): KnownArtifactKind | undefined {
	const entry = Object.entries(ARTIFACT_KIND_SLUGS).find(([, value]) => value === slug);
	return entry?.[0] as KnownArtifactKind | undefined;
}

export interface ReviewArtifactProps {
	artifact: ReviewArtifactDisplay | undefined;
	variant?: "summary" | "label";
	display?: "compact" | "full";
	className?: string;
}

export type ReviewArtifactLinkProps = ReviewArtifactProps;

export function reviewArtifactLabel(artifact: ReviewArtifactDisplay): string {
	switch (artifact.type) {
		case ARTIFACT_KIND.pullRequest:
			if (artifact.provider === "GITLAB")
				return artifact.number == null ? "Merge request" : `MR !${artifact.number}`;
			return artifact.number == null ? "Pull request" : `PR #${artifact.number}`;
		case ARTIFACT_KIND.issue:
			return artifact.number == null ? "Issue" : `Issue #${artifact.number}`;
		case ARTIFACT_KIND.conversationThread:
			return artifact.channelName ? `#${artifact.channelName}` : "Conversation";
		default:
			// A kind this build has no copy for still names itself rather than rendering blank.
			return artifactKindLabel(artifact.type);
	}
}

export function reviewArtifactScopeLabel(
	kind: string,
	id: number | undefined,
	artifact: ReviewArtifactDisplay | undefined,
): string {
	if (id != null && artifact) {
		return [artifact.repositoryName, reviewArtifactLabel(artifact)].filter(Boolean).join(" · ");
	}
	const labels: Record<KnownArtifactKind, readonly [string, string]> = {
		[ARTIFACT_KIND.pullRequest]: ["pull or merge request", "pull or merge requests"],
		[ARTIFACT_KIND.issue]: ["issue", "issues"],
		[ARTIFACT_KIND.conversationThread]: ["conversation", "conversations"],
	};
	const scope = isKnownArtifactKind(kind)
		? labels[kind][id == null ? 1 : 0]
		: artifactKindLabel(kind).toLowerCase();
	return `${id == null ? "All" : "One"} ${scope}`;
}

function ArtifactIcon({ type }: { type: ReviewArtifactDisplay["type"] }) {
	if (type === ARTIFACT_KIND.pullRequest) return <GitPullRequestIcon aria-hidden />;
	if (type === ARTIFACT_KIND.issue) return <CircleDotIcon aria-hidden />;
	return <MessagesSquareIcon aria-hidden />;
}

export function ReviewArtifact({
	artifact,
	variant = "summary",
	display = "compact",
	className,
}: ReviewArtifactProps) {
	if (!artifact) {
		return <span className={cn("text-sm text-muted-foreground", className)}>No reviewed work</span>;
	}

	return (
		<span className={cn("flex w-full min-w-0 gap-2 whitespace-normal text-sm", className)}>
			<ReviewArtifactContent artifact={artifact} variant={variant} display={display} />
		</span>
	);
}

export function ReviewArtifactLink({
	artifact,
	variant = "summary",
	display = "compact",
	className,
}: ReviewArtifactLinkProps) {
	if (!artifact?.url) {
		return (
			<ReviewArtifact
				artifact={artifact}
				variant={variant}
				display={display}
				className={className}
			/>
		);
	}

	return (
		<a
			href={artifact.url}
			target="_blank"
			rel="noopener noreferrer"
			className={cn(
				"flex w-full min-w-0 gap-2 whitespace-normal text-sm hover:underline",
				className,
			)}
		>
			<ReviewArtifactContent artifact={artifact} variant={variant} display={display}>
				<ExternalLinkIcon className="size-3.5 shrink-0" aria-hidden />
			</ReviewArtifactContent>
			<span className="sr-only"> (opens in a new tab)</span>
		</a>
	);
}

function ReviewArtifactContent({
	artifact,
	variant,
	display,
	children,
}: {
	artifact: ReviewArtifactDisplay;
	variant: NonNullable<ReviewArtifactProps["variant"]>;
	display: NonNullable<ReviewArtifactProps["display"]>;
	children?: ReactNode;
}) {
	const artifactLabel = reviewArtifactLabel(artifact);
	const label = [artifact.repositoryName, artifactLabel].filter(Boolean).join(" · ");
	return (
		<>
			<span className="mt-0.5 shrink-0 [&>svg]:size-4">
				<ArtifactIcon type={artifact.type} />
			</span>
			<span className="min-w-0">
				<span className="flex items-center gap-1 font-medium">
					<span className={cn(display === "compact" ? "truncate" : "break-words")}>{label}</span>
					{children}
				</span>
				{variant === "summary" && artifact.title !== artifactLabel && (
					<span
						className={cn(
							"text-muted-foreground",
							display === "compact" ? "line-clamp-2" : "break-words",
						)}
					>
						{artifact.title}
					</span>
				)}
			</span>
		</>
	);
}
