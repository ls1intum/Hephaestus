import { Link } from "@tanstack/react-router";
import type { ReflectionFeedback } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { ReflectionMessage } from "./ReflectionMessage";

/** The reflection lane's own icon, taken from the delivery-place registry rather than picked again. */
const PlaceIcon = DELIVERY_PLACE_DEFS.REFLECTION.icon;

export interface ReflectionPageProps {
	workspaceSlug: string;
	/** Newest first, as the endpoint sends them. `undefined` while loading or after a failure. */
	feedback: ReflectionFeedback[] | undefined;
	isLoading: boolean;
	error: unknown;
	onRetry: () => void;
}

/**
 * A developer's own reflection surface: the practice feedback prepared for them and for nobody else.
 *
 * <p>This is the process-level lane of the three. Feedback on the work itself answers "what is wrong
 * here, in this change"; the mentor conversation asks the developer to judge their own work; this
 * page names what recurs across several pieces of their work and what to do differently next time.
 * The layout is built around that: one heading per pattern, the practice it belongs to, the work it
 * was seen on, and the next step at the end of the composed text. There is nowhere to put a verdict
 * on the person and nowhere to put a figure.
 *
 * <p>One bordered container with the patterns as rows inside it, rather than a card each. A page of
 * cards reads as a set of separate objects to be counted; this is one short list of things to work
 * on, and its length is not information.
 */
export function ReflectionPage({
	workspaceSlug,
	feedback,
	isLoading,
	error,
	onRetry,
}: ReflectionPageProps) {
	const messages = feedback ?? [];

	return (
		<div className="min-w-0 space-y-6">
			<PageHeader
				icon={<PlaceIcon />}
				title="My feedback"
				description="Practice feedback prepared for you alone. Feedback on the work itself says what to change in that one piece of work — this page is about what recurs across several of them, and what to do differently next time. Nobody else in this workspace can read what it says."
			/>
			<section aria-labelledby="reflection-heading" className="min-w-0 space-y-3">
				<h2 id="reflection-heading" className="text-lg font-semibold">
					What recurs in your work
				</h2>
				{error ? (
					<QueryErrorAlert error={error} title="Couldn't load your feedback" onRetry={onRetry} />
				) : isLoading ? (
					<ReflectionSkeleton />
				) : messages.length === 0 ? (
					<ReflectionEmpty workspaceSlug={workspaceSlug} />
				) : (
					<div className="min-w-0 divide-y rounded-lg border">
						{messages.map((message) => (
							<ReflectionMessage
								key={message.id}
								workspaceSlug={workspaceSlug}
								feedback={message}
							/>
						))}
					</div>
				)}
			</section>
		</div>
	);
}

/**
 * The common case for anyone new, so it is written as a state of the work and not a state of the
 * reader: what has to be true before anything appears, and what an empty page does and does not
 * mean. No encouragement aimed at the person — that is the level of feedback this whole surface
 * exists to avoid — and no apology either.
 *
 * <p>The way out is the member-facing review activity, which is where "has anything been reviewed at
 * all?" is actually answered. Never an operator surface: the text on this page is withheld from
 * those on purpose, and a link from here would imply otherwise.
 */
function ReflectionEmpty({ workspaceSlug }: { workspaceSlug: string }) {
	return (
		<Empty className="border">
			<EmptyHeader>
				<EmptyMedia variant="icon">
					<PlaceIcon />
				</EmptyMedia>
				<EmptyTitle>No feedback prepared for you yet</EmptyTitle>
				<EmptyDescription>
					Feedback arrives here once a practice review has observed the same thing in more than one
					piece of your work. A single observation stays on the work it was made about, so this page
					fills in as you go — and an empty page means there is nothing recurring to say, not that
					anything went wrong.
				</EmptyDescription>
			</EmptyHeader>
			<EmptyContent>
				<Link
					to="/w/$workspaceSlug/reviews"
					params={{ workspaceSlug }}
					className="underline underline-offset-4 hover:no-underline"
				>
					See what has been reviewed in this workspace
				</Link>
			</EmptyContent>
		</Empty>
	);
}

/**
 * Two blocks, not the page cap of twenty: this list is short by design, and a skeleton the height of
 * a full page would drop the reader a screen below the content that arrives.
 */
function ReflectionSkeleton() {
	return (
		<div className="min-w-0 divide-y rounded-lg border" role="status">
			<span className="sr-only">Loading your feedback</span>
			{[0, 1].map((index) => (
				<div key={index} className="space-y-3 p-4 sm:p-6">
					<Skeleton className="h-5 w-full max-w-72" />
					<Skeleton className="h-3.5 w-full max-w-56" />
					<div className="space-y-2">
						<Skeleton className="h-3.5 w-full" />
						<Skeleton className="h-3.5 w-full" />
						<Skeleton className="h-3.5 w-full max-w-md" />
					</div>
					<Skeleton className="h-3.5 w-full max-w-40" />
				</div>
			))}
		</div>
	);
}
