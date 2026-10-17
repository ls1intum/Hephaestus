import { cn } from "cn";
import type { ItemRef } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import { hasText } from "@/lib/text";

function qualifiedLabel(item: ItemRef): string {
	return [item.container, item.label].filter(hasText).join(" · ");
}

export interface ReviewItemProps {
	/**
	 * The item as the wire names it, provider included: the mark is the ref's own, so no caller
	 * passes a provider beside it and none can pass one the ref disagrees with.
	 */
	item: ItemRef | undefined;
	className?: string;
}

/**
 * Never the item's title: it is long, and every surface that shows one already has somewhere better
 * to put it — the review row uses it as the row's own name, the review page as the heading.
 */
export function ReviewItemLabel({ item, className }: ReviewItemProps) {
	if (!item) {
		return <span className={cn("text-muted-foreground", className)}>No reviewed work</span>;
	}
	const Icon = reviewedWorkIcon(item.kind, item.provider ?? undefined);
	return (
		<span className={cn("inline-flex max-w-full min-w-0 items-center gap-1.5", className)}>
			<Icon className="size-3.5 shrink-0" />
			<span className="min-w-0 break-words">{qualifiedLabel(item)}</span>
		</span>
	);
}

export function ReviewItemLink({ item, className }: ReviewItemProps) {
	if (item === undefined || !hasText(item.url)) {
		return <ReviewItemLabel item={item} className={className} />;
	}
	const Icon = reviewedWorkIcon(item.kind, item.provider ?? undefined);
	return (
		<InlineLink
			href={item.url}
			external
			className={cn("relative inline-flex max-w-full min-w-0 items-center gap-1.5", className)}
		>
			<Icon className="size-3.5 shrink-0" />
			<span className="min-w-0 break-words">{qualifiedLabel(item)}</span>
		</InlineLink>
	);
}
