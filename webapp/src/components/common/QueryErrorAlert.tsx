import { AlertCircleIcon, InfoIcon, LockIcon, SearchXIcon } from "lucide-react";
import type * as React from "react";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export interface QueryErrorAlertProps {
	/** The thrown request error; its ProblemDetail body supplies the message and the HTTP status. */
	error: unknown;
	/** What failed, in the reader's terms — e.g. "Could not load your mirrored collections". */
	title: string;
	/**
	 * Retries the failed query. Omit when the user cannot retry the error away. Even when supplied, the
	 * button is withheld for statuses an identical retry cannot change (see {@link classifyError}).
	 */
	onRetry?: () => void;
	className?: string;
}

interface ErrorClass {
	icon: React.ReactNode;
	/** What the reader can do about it, as opposed to the server's `detail`, which says what happened. */
	guidance: string;
	variant: "destructive" | "warning";
	/**
	 * Whether re-issuing the identical request could succeed. False for anything the server has already
	 * decided — authz, absence, conflict, malformed request — which needs a different request, different
	 * permissions, or a reload instead.
	 */
	retryable: boolean;
}

/** Map an HTTP status onto an icon, guidance, variant, and whether a Retry button could help. */
function classifyError(status: number | undefined): ErrorClass {
	// No status: the request never reached a server (offline, DNS, CORS, abort), so retrying is right
	// once the network is back.
	if (status == null) {
		return {
			icon: <AlertCircleIcon />,
			guidance: "Check your connection, then try again.",
			variant: "destructive",
			retryable: true,
		};
	}
	if (status === 401) {
		return {
			icon: <LockIcon />,
			guidance: "Your session has expired. Sign in again to continue.",
			variant: "warning",
			retryable: false,
		};
	}
	if (status === 403) {
		return {
			icon: <LockIcon />,
			guidance: "You don't have permission to view this. Ask an admin for access.",
			variant: "destructive",
			retryable: false,
		};
	}
	if (status === 404) {
		return {
			icon: <SearchXIcon />,
			guidance: "It may have been deleted or moved. Reload the page to see the current state.",
			variant: "destructive",
			retryable: false,
		};
	}
	if (status === 409) {
		// Often not a real failure — something else already changed it. Warning, not destructive, and
		// no Retry: the answer would be the same.
		return {
			icon: <InfoIcon />,
			guidance: "Something else changed this first. Reload the page to see the current state.",
			variant: "warning",
			retryable: false,
		};
	}
	if (status === 429) {
		return {
			icon: <InfoIcon />,
			guidance: "Too many requests for now. Wait a moment, then try again.",
			variant: "warning",
			retryable: true,
		};
	}
	if (status >= 500) {
		return {
			icon: <AlertCircleIcon />,
			guidance: "Something went wrong on our side. Trying again usually helps.",
			variant: "destructive",
			retryable: true,
		};
	}
	// Remaining 4xx (400, 422, …): the server rejected the request itself, so an identical retry is
	// rejected identically.
	return {
		icon: <AlertCircleIcon />,
		guidance: "The request wasn't accepted. Reload the page and try again.",
		variant: "destructive",
		retryable: false,
	};
}

/**
 * Join the server's `detail` to our guidance. `detail` strings are not reliably punctuated, so
 * terminate the sentence before appending rather than emit "Rate limit exceeded Wait a moment".
 */
function describe(detail: string, guidance: string): string {
	const lead = detail.trim();
	if (lead.length === 0 || lead === guidance) {
		return guidance;
	}
	return /[.!?]$/.test(lead) ? `${lead} ${guidance}` : `${lead}. ${guidance}`;
}

/**
 * Shared failed-query surface: every network-loading section renders this on error, so failures look
 * the same and only offer a Retry when one could actually work.
 */
export function QueryErrorAlert({ error, title, onRetry, className }: QueryErrorAlertProps) {
	const status = problemStatusOf(error);
	const { icon, guidance, variant, retryable } = classifyError(status);
	// `detail` (what happened) is more specific than anything we can infer, so it leads; guidance
	// (what to do) follows. Falling `detail` back to guidance avoids repeating it when the server
	// said nothing useful.
	const detail = problemDetailOf(error, guidance);
	const showRetry = onRetry != null && retryable;

	return (
		<Alert variant={variant} className={className}>
			{icon}
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>{describe(detail, guidance)}</AlertDescription>
			{showRetry && (
				<AlertAction>
					<Button type="button" variant="outline" size="sm" onClick={onRetry}>
						Retry
					</Button>
				</AlertAction>
			)}
		</Alert>
	);
}
