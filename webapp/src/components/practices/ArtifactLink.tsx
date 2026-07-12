import { formatDistanceToNow } from "date-fns";
import type { PracticeReportItem } from "@/components/practices/practice-types";
import { ArtifactStateIcon } from "@/components/practices/status-language";
import { cn } from "@/lib/utils";

/**
 * Relative time for an observation timestamp. The generated type says Date, but the runtime
 * value is the raw ISO string (the client does not wire response transformers), so coerce.
 */
export function formatWhen(date: Date | string): string {
	return formatDistanceToNow(new Date(date), { addSuffix: true });
}

export interface ArtifactLinkProps {
	item: PracticeReportItem;
	className?: string;
}

/**
 * The one way an artifact is referenced everywhere on the practice surfaces: provider state
 * icon, deep-linked title, then repo, number and relative time in muted text. Every practice
 * signal stays anchored to the concrete PR or issue it came from. An artifact without a
 * linkable page (a conversation thread, a since-deleted repo) renders a plain label instead
 * of a dead link.
 */
export function ArtifactLink({ item, className }: ArtifactLinkProps) {
	const repoShortName = item.artifactRepository?.split("/")[1];
	const reference = [
		repoShortName,
		item.artifactNumber != null ? `#${item.artifactNumber}` : undefined,
	]
		.filter(Boolean)
		.join(" ");
	const fallbackLabel =
		item.artifactType === "CONVERSATION_THREAD" ? "A team conversation" : "A work item";
	return (
		<span className={cn("flex min-w-0 items-center gap-1.5 text-sm", className)}>
			<ArtifactStateIcon kind={item.artifactType} state={item.artifactState} />
			{item.artifactUrl ? (
				<a
					href={item.artifactUrl}
					target="_blank"
					rel="noreferrer"
					className="min-w-0 truncate font-medium text-foreground hover:underline"
				>
					{item.artifactTitle ?? item.artifactUrl}
				</a>
			) : (
				<span className="min-w-0 truncate font-medium text-foreground">
					{item.artifactTitle ?? fallbackLabel}
				</span>
			)}
			<span className="shrink-0 text-xs text-muted-foreground">
				{reference ? `${reference} · ` : ""}
				{formatWhen(item.observedAt)}
			</span>
		</span>
	);
}
