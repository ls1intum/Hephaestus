import type { AnchorHTMLAttributes, HTMLAttributes } from "react";
import { Streamdown } from "streamdown";
import { MarkdownCode } from "@/components/common/MarkdownCode";
import {
	type DeliveryFacts,
	deliveryOutcome,
} from "@/components/practice-vocabulary/delivery-outcome-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * Everything the card reads, and it takes the record rather than three fields off it. The outcome
 * depends on all of them together, so a caller that could pass a state without its channel could
 * label a conversation unit with words that only make sense on the work.
 */
export type FeedbackMessageFeedback = DeliveryFacts & { body?: string };

export interface FeedbackMessageProps {
	feedback: FeedbackMessageFeedback;
	className?: string;
}

const HTTP_URL = /^https?:\/\//i;

function SafeAnchor({ href, children, className }: AnchorHTMLAttributes<HTMLAnchorElement>) {
	if (typeof href !== "string" || !HTTP_URL.test(href)) {
		return <span className={className}>{children}</span>;
	}
	return (
		<a href={href} className={className} rel="noopener noreferrer" target="_blank">
			{children}
		</a>
	);
}

function PreviewHeading({ children, className }: HTMLAttributes<HTMLHeadingElement>) {
	return <h4 className={className}>{children}</h4>;
}

const UNTRUSTED_MARKDOWN_COMPONENTS = {
	a: SafeAnchor,
	code: MarkdownCode,
	img: () => null,
	h1: PreviewHeading,
	h2: PreviewHeading,
	h3: PreviewHeading,
	h4: PreviewHeading,
	h5: PreviewHeading,
	h6: PreviewHeading,
};

/**
 * The composed feedback as the developer would read it, under one badge saying what became of it.
 *
 * The header used to be a tinted panel carrying its own five-case copy table — a fifth set of words
 * for the delivery states, and the place the owner found "Ready for a future conversation with Heph.
 * It has not been delivered." The narrative moved to `DeliveryTrace`, which is where a reader looks
 * for it.
 *
 * <p>What is left is one badge, and only on text that did *not* simply reach the developer. Badging
 * the ordinary case would colour every card and put a second "Delivered" on a page whose Delivery
 * section already says so. Text that was withheld, failed, is still queued, or has since been
 * replaced is the case that needs marking, because it can otherwise be quoted as though it was sent.
 */
export function FeedbackMessage({ feedback, className }: FeedbackMessageProps) {
	const { body } = feedback;
	const unsent = feedback.deliveryState !== "DELIVERED";

	return (
		<Card className={cn("gap-0 border py-0", className)}>
			{unsent && (
				<CardHeader className="border-b py-3">
					<StatusBadge def={deliveryOutcome(feedback)} />
				</CardHeader>
			)}
			{body ? (
				<CardContent className="prose prose-sm dark:prose-invert max-w-none break-words py-4">
					<Streamdown
						mode="static"
						rehypePlugins={[]}
						remarkRehypeOptions={{ allowDangerousHtml: false }}
						components={UNTRUSTED_MARKDOWN_COMPONENTS}
					>
						{body}
					</Streamdown>
				</CardContent>
			) : (
				<CardContent className="py-4 text-sm text-muted-foreground">
					No feedback text was composed for this record.
				</CardContent>
			)}
		</Card>
	);
}
