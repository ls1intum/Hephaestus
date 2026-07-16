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
	 * Retries the failed query. Omit for an error the user cannot retry away. Supplying it is a
	 * request, not a guarantee: the button is withheld for statuses an identical retry cannot change
	 * (see {@link classifyError}), because a Retry that cannot work is a lie about the way out.
	 */
	onRetry?: () => void;
	className?: string;
}

interface ErrorClass {
	/** Icon that matches the kind of failure, so the alert reads before it is read. */
	icon: React.ReactNode;
	/** What the reader can do about it. Complements the server's `detail`, which says what happened. */
	guidance: string;
	variant: "destructive" | "warning";
	/**
	 * Whether re-issuing the identical request could plausibly succeed. False for anything the server
	 * has already decided (authz, absence, conflict, malformed request) — those need a different
	 * request, different permissions, or a reload, and none of that is what a Retry button does.
	 */
	retryable: boolean;
}

/**
 * Map an HTTP status onto the way out. The distinction that matters is not "error vs not" but
 * "can the reader do something, and is retrying it?" — a 403 and a 503 are both failures, but only
 * one of them gets better if you press a button.
 */
function classifyError(status: number | undefined): ErrorClass {
	// No status at all: the request never reached a server (offline, DNS, CORS, abort). Retrying is
	// exactly the right move once the network is back.
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
		// Usually not a failure at all — something else already holds the thing, or already did the
		// work. Warning rather than destructive, and no Retry: the answer would be the same.
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
 * Run the server's account of what happened into our account of what to do about it. Server `detail`
 * strings are not reliably punctuated, so terminate the sentence before appending rather than emit
 * "Rate limit exceeded Wait a moment, then try again."
 */
function describe(detail: string, guidance: string): string {
	const lead = detail.trim();
	if (lead.length === 0 || lead === guidance) {
		return guidance;
	}
	return /[.!?]$/.test(lead) ? `${lead} ${guidance}` : `${lead}. ${guidance}`;
}

/**
 * The one failed-query surface. Every section that loads over the network renders this on error, so a
 * failure always looks the same — and, just as importantly, only offers a way out that exists.
 */
export function QueryErrorAlert({ error, title, onRetry, className }: QueryErrorAlertProps) {
	const status = problemStatusOf(error);
	const { icon, guidance, variant, retryable } = classifyError(status);
	// The server's `detail` says what happened and is more specific than anything we can infer, so it
	// leads; the status-derived guidance says what to do about it. Falling `detail` back to the
	// guidance keeps the description from repeating itself when the server said nothing useful.
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
