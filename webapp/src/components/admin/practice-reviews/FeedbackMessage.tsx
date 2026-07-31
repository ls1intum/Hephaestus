import {
	AlertTriangleIcon,
	CheckCircle2Icon,
	Clock3Icon,
	EyeOffIcon,
	HistoryIcon,
} from "lucide-react";
import type { AnchorHTMLAttributes, HTMLAttributes } from "react";
import { Streamdown } from "streamdown";
import { MarkdownCode } from "@/components/common/MarkdownCode";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import {
	type FeedbackDeliveryState,
	type FeedbackSuppressionReason,
	SUPPRESSION_REASON_LABELS,
} from "./review-format";

export interface FeedbackMessageProps {
	body: string | undefined;
	deliveryState: FeedbackDeliveryState;
	suppressionReason?: FeedbackSuppressionReason;
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

export function FeedbackMessage({
	body,
	deliveryState,
	suppressionReason,
	className,
}: FeedbackMessageProps) {
	const outcome = feedbackOutcome(deliveryState, suppressionReason);
	const Icon = outcome.icon;

	return (
		<Card className={cn("gap-0 border py-0", outcome.frameClass, className)}>
			<CardHeader
				className={cn("grid grid-cols-[auto_1fr] gap-x-2 border-b py-3", outcome.headerClass)}
			>
				<Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
				<CardTitle className="text-sm">{outcome.title}</CardTitle>
				<CardDescription className="col-start-2">{outcome.description}</CardDescription>
			</CardHeader>
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
					No composed message is available for this record.
				</CardContent>
			)}
		</Card>
	);
}

type FeedbackOutcome = {
	title: string;
	description: string;
	icon: typeof CheckCircle2Icon;
	frameClass?: string;
	headerClass?: string;
};

function feedbackOutcome(
	state: FeedbackDeliveryState,
	reason: FeedbackSuppressionReason | undefined,
): FeedbackOutcome {
	switch (state) {
		case "DELIVERED":
			return {
				title: "Delivered",
				description: "This message was delivered.",
				icon: CheckCircle2Icon,
				headerClass: "bg-success/5 text-success",
			};
		case "SUPERSEDED":
			return {
				title: "Delivered, then updated",
				description: "A newer message replaced this version.",
				icon: HistoryIcon,
			};
		case "PREPARED":
			return {
				title: "Awaiting conversation",
				description: "Ready for a future conversation with Heph. It has not been delivered.",
				icon: Clock3Icon,
			};
		case "FAILED":
			return {
				title: "Delivery failed",
				description: "Hephaestus prepared this message but could not deliver it.",
				icon: AlertTriangleIcon,
				frameClass: "border-destructive/40",
				headerClass: "bg-destructive/5 text-destructive",
			};
		case "SUPPRESSED":
			return {
				title: "Not delivered",
				description: reason
					? `${SUPPRESSION_REASON_LABELS[reason]}.`
					: "Why this message was withheld is unavailable.",
				icon: EyeOffIcon,
				frameClass: "border-warning/50",
				headerClass: "bg-warning/5 text-warning",
			};
	}
}
