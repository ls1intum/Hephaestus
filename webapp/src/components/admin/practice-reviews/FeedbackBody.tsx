import { CodeIcon, TextIcon } from "lucide-react";
import { type AnchorHTMLAttributes, type HTMLAttributes, useState } from "react";
import { Streamdown } from "streamdown";
import { MarkdownCode } from "@/components/common/MarkdownCode";
import {
	type DeliveryFacts,
	deliveryOutcome,
} from "@/components/practice-vocabulary/delivery-outcome-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
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

type FeedbackView = "rendered" | "source";

/**
 * The header badge appears only on text that did *not* simply reach the developer: badging the
 * ordinary case would colour every card and repeat what the surrounding page already says, while
 * text that was withheld, failed, is queued or has been replaced can otherwise be read as sent.
 */
export function FeedbackBody({ feedback, className }: FeedbackBodyProps) {
	const [view, setView] = useState<FeedbackView>("rendered");
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
			<CardHeader className="flex flex-wrap items-center justify-between gap-2 border-b py-3">
				{unsent ? <StatusBadge def={deliveryOutcome(feedback)} /> : <span />}
				{/* The group holds the current view, so "exactly one is pressed" is the control's
				    invariant rather than something two `onClick`s keep by hand. Deselecting the pressed
				    item is refused below — there is no third state in which the body shows nothing. */}
				<ToggleGroup
					variant="outline"
					size="sm"
					value={[view]}
					onValueChange={(next) => {
						const chosen = next[0] as FeedbackView | undefined;
						if (chosen) setView(chosen);
					}}
					aria-label="How to show the feedback"
				>
					<ToggleGroupItem value="rendered">
						<TextIcon aria-hidden />
						Rendered
					</ToggleGroupItem>
					<ToggleGroupItem value="source">
						<CodeIcon aria-hidden />
						Source
					</ToggleGroupItem>
				</ToggleGroup>
			</CardHeader>
			<CardContent className="py-4">
				{view === "rendered" ? (
					<div className={FEEDBACK_PROSE}>
						<Streamdown
							mode="static"
							rehypePlugins={[]}
							remarkRehypeOptions={{ allowDangerousHtml: false }}
							components={UNTRUSTED_MARKDOWN_COMPONENTS}
						>
							{body}
						</Streamdown>
					</div>
				) : (
					<pre className="max-h-96 overflow-auto whitespace-pre-wrap break-words rounded-md bg-muted p-3 text-xs">
						{body}
					</pre>
				)}
			</CardContent>
		</Card>
	);
}
