import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
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
					<TabsContent value="rendered" className={UNTRUSTED_MARKDOWN_PROSE}>
						<UntrustedMarkdown>{body}</UntrustedMarkdown>
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
