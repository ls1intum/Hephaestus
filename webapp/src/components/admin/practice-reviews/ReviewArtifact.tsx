import {
	CircleDotIcon,
	ExternalLinkIcon,
	GitPullRequestIcon,
	MessagesSquareIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import type { ReviewArtifact as ReviewArtifactData, ReviewRunTarget } from "@/api/types.gen";
import { cn } from "@/lib/utils";

export type ReviewArtifactDisplay = ReviewArtifactData | ReviewRunTarget;
const ARTIFACT_TYPE_SLUGS = {
	PULL_REQUEST: "pull-request",
	ISSUE: "issue",
	CONVERSATION_THREAD: "conversation",
} as const satisfies Record<ReviewArtifactData["type"], string>;

export type ReviewArtifactTypeSlug = (typeof ARTIFACT_TYPE_SLUGS)[keyof typeof ARTIFACT_TYPE_SLUGS];

export function reviewArtifactTypeSlug(type: ReviewArtifactData["type"]): ReviewArtifactTypeSlug {
	return ARTIFACT_TYPE_SLUGS[type];
}

export function reviewArtifactTypeFromSlug(slug: string): ReviewArtifactData["type"] | undefined {
	const entry = Object.entries(ARTIFACT_TYPE_SLUGS).find(([, value]) => value === slug);
	return entry?.[0] as ReviewArtifactData["type"] | undefined;
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
		case "PULL_REQUEST":
			if (artifact.provider === "GITLAB")
				return artifact.number == null ? "Merge request" : `MR !${artifact.number}`;
			return artifact.number == null ? "Pull request" : `PR #${artifact.number}`;
		case "ISSUE":
			return artifact.number == null ? "Issue" : `Issue #${artifact.number}`;
		case "CONVERSATION_THREAD":
			return artifact.channelName ? `#${artifact.channelName}` : "Conversation";
	}
}

export function reviewArtifactScopeLabel(
	type: ReviewArtifactData["type"],
	id: number | undefined,
	artifact: ReviewArtifactDisplay | undefined,
): string {
	if (id != null && artifact) {
		return [artifact.repositoryName, reviewArtifactLabel(artifact)].filter(Boolean).join(" · ");
	}
	const labels = {
		PULL_REQUEST: ["pull or merge request", "pull or merge requests"],
		ISSUE: ["issue", "issues"],
		CONVERSATION_THREAD: ["conversation", "conversations"],
	} as const;
	return `${id == null ? "All" : "One"} ${labels[type][id == null ? 1 : 0]}`;
}

function ArtifactIcon({ type }: { type: ReviewArtifactDisplay["type"] }) {
	if (type === "PULL_REQUEST") return <GitPullRequestIcon aria-hidden />;
	if (type === "ISSUE") return <CircleDotIcon aria-hidden />;
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
