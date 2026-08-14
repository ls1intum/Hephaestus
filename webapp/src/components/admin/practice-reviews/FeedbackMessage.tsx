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
 * Tailwind Typography sizes its vertical rhythm for an article, where a heading opens a section the
 * reader has scrolled to. This is a short comment in a card: three headings and a list inside a few
 * hundred words, and the default `mt-8` above each one left a gap the product owner measured as
 * "almost doubling what you would expect". Tightened to a rhythm that suits the length of the thing.
 */
const FEEDBACK_PROSE =
	"prose prose-sm dark:prose-invert max-w-none break-words prose-headings:mt-4 prose-headings:mb-1.5 prose-headings:text-sm prose-headings:font-semibold prose-p:my-2 prose-ul:my-2 prose-ol:my-2 prose-li:my-0.5 prose-pre:my-2 first:prose-headings:mt-0";

type FeedbackView = "rendered" | "source";

/**
 * The composed feedback, as the developer would read it or as it was actually written.
 *
 * <p>The source used to sit in a collapsed accordion below the card labelled "View Markdown source" —
 * a second control, in a second place, that pushed the page down when opened and showed the same text
 * twice. It is a view of one thing, so it is a switch on the thing: two toggles in the card's own
 * header, and the body swaps underneath them.
 *
 * <p>The badge in that header appears only on text that did *not* simply reach the developer. Badging
 * the ordinary case would colour every card and put a second "Delivered" on a page whose Delivery
 * section already says so. Text that was withheld, failed, is still queued, or has since been replaced
 * is the case that needs marking, because it can otherwise be quoted as though it was sent.
 */
export function FeedbackMessage({ feedback, className }: FeedbackMessageProps) {
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
				<ToggleGroup
					value={[view]}
					onValueChange={(next: string[]) => {
						// A toggle group can be emptied by re-pressing the active item; the body must always
						// be showing something, so an empty selection keeps the current view.
						const [chosen] = next;
						if (chosen === "rendered" || chosen === "source") setView(chosen);
					}}
					variant="outline"
					size="sm"
					aria-label="How to show the feedback"
				>
					<ToggleGroupItem value="rendered" aria-label="Show the feedback as the developer sees it">
						<TextIcon aria-hidden />
						Rendered
					</ToggleGroupItem>
					<ToggleGroupItem value="source" aria-label="Show the Markdown that was composed">
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
