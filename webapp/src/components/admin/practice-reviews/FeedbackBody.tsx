import type { AnchorHTMLAttributes, HTMLAttributes } from "react";
import { Streamdown } from "streamdown";
import { MarkdownCode } from "@/components/common/MarkdownCode";
import {
	type DeliveryFacts,
	deliveryOutcome,
} from "@/components/practice-vocabulary/delivery-outcome-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

/**
 * The whole record rather than the three fields read off it: the outcome depends on all of them
 * together, so a caller able to pass a state without its channel could label a conversation unit
 * with words that only make sense on the work.
 */
export type FeedbackBodyFeedback = DeliveryFacts & { body?: string };

export interface FeedbackBodyProps {
	feedback: FeedbackBodyFeedback;
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
 * Two departures from Tailwind Typography's defaults, which are sized for an article rather than a
 * short comment inside a card.
 *
 * <p>The heading rhythm is tightened, because `mt-8` above a heading assumes a section the reader
 * scrolled to rather than one of three inside a few hundred words.
 *
 * <p>`pre` gives up `overflow-x: auto` and wraps instead. The composer quotes the developer's own
 * code in fenced blocks, and a block wider than the card becomes a scrollable region no keyboard can
 * reach — axe's `scrollable-region-focusable`, and a real problem on a phone, where the end of the
 * line needs a horizontal drag inside a vertically scrolling page.
 */
const FEEDBACK_PROSE =
	"prose prose-sm dark:prose-invert max-w-none break-words prose-headings:mt-4 prose-headings:mb-1.5 prose-headings:text-sm prose-headings:font-semibold prose-p:my-2 prose-ul:my-2 prose-ol:my-2 prose-li:my-0.5 prose-pre:my-2 prose-pre:overflow-x-visible prose-pre:whitespace-pre-wrap prose-pre:break-words first:prose-headings:mt-0";

/**
 * The header badge appears only on text that did *not* simply reach the developer: badging the
 * ordinary case would colour every card and repeat what the surrounding page already says, while
 * text that was withheld, failed, is prepared or has been replaced can otherwise be read as sent.
 *
 * <p>Rendered and Source are two views of one body, so they are `Tabs`: the primitive holds "exactly
 * one view is showing", gives each view a `role="tab"` with `aria-selected`, ties the body to the
 * control that chose it, and answers the arrow keys. Nothing here keeps that by hand.
 */
export function FeedbackBody({ feedback, className }: FeedbackBodyProps) {
	const { body } = feedback;
	const unsent = feedback.deliveryState !== "DELIVERED";

	if (!body) {
		return (
			<Card className={cn("gap-0 border py-0", className)}>
				<CardContent className="py-4 text-sm text-muted-foreground">
					No feedback text was composed for this record.
				</CardContent>
			</Card>
		);
	}

	return (
		<Card className={cn("gap-0 border py-0", className)}>
			{/* No separator and no band: the note is what this card is for, and a ruled strip above it
			    spends height saying only that a two-word switch lives there. The switch sits on the
			    body's own left edge, so the row still reads as a row when nothing is badged. */}
			<Tabs defaultValue="rendered" className="gap-0">
				<CardHeader className="flex flex-wrap items-center justify-between gap-2 pt-3 pb-2">
					<TabsList aria-label="How to show the feedback">
						<TabsTrigger value="rendered" className="px-3">
							Rendered
						</TabsTrigger>
						<TabsTrigger value="source" className="px-3">
							Source
						</TabsTrigger>
					</TabsList>
					{unsent && <StatusBadge def={deliveryOutcome(feedback)} />}
				</CardHeader>
				<CardContent className="pb-4">
					<TabsContent value="rendered" className={FEEDBACK_PROSE}>
						<Streamdown
							mode="static"
							rehypePlugins={[]}
							remarkRehypeOptions={{ allowDangerousHtml: false }}
							components={UNTRUSTED_MARKDOWN_COMPONENTS}
						>
							{body}
						</Streamdown>
					</TabsContent>
					{/* Uncapped, like the rendered view: a height cap here would make the same text a
					    scrollable region in one view and not the other, and a scroll box with no
					    tabindex is unreachable by keyboard. Wrapping keeps it off the horizontal axis. */}
					<TabsContent value="source">
						<pre className="whitespace-pre-wrap break-words rounded-md bg-muted p-3 text-xs">
							{body}
						</pre>
					</TabsContent>
				</CardContent>
			</Tabs>
		</Card>
	);
}
