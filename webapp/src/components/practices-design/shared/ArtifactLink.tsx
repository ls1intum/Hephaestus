import { formatDistanceToNow } from "date-fns";
import { ArtifactStateIcon } from "@/components/practices-design/shared/status-language";
import type { ArtifactRef } from "@/components/practices-design/shared/types";
import { cn } from "@/lib/utils";

export function formatWhen(iso: string): string {
	return formatDistanceToNow(new Date(iso), { addSuffix: true });
}

export interface ArtifactLinkProps {
	artifact: ArtifactRef;
	/** When the observation was made. Rendered as a relative time next to the repo. */
	observedAt?: string;
	className?: string;
}

/**
 * The one way an artifact is referenced everywhere in this exploration: provider state icon,
 * deep-linked title, then repo, number and relative time in muted text. Every practice signal
 * stays anchored to the concrete PR or issue it came from.
 */
export function ArtifactLink({ artifact, observedAt, className }: ArtifactLinkProps) {
	return (
		<span className={cn("flex min-w-0 items-center gap-1.5 text-sm", className)}>
			<ArtifactStateIcon kind={artifact.kind} state={artifact.state} />
			<a
				href={artifact.url}
				target="_blank"
				rel="noreferrer"
				className="min-w-0 truncate font-medium text-foreground hover:underline"
			>
				{artifact.title}
			</a>
			<span className="shrink-0 text-xs text-muted-foreground">
				{artifact.repo.split("/")[1]} #{artifact.number}
				{observedAt ? ` · ${formatWhen(observedAt)}` : null}
			</span>
		</span>
	);
}
