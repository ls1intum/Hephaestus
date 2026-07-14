import { AlertCircleIcon } from "lucide-react";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { problemDetailOf } from "@/lib/problem-detail";

export interface QueryErrorAlertProps {
	/** The thrown request error; its ProblemDetail body supplies the message. */
	error: unknown;
	/** What failed, in the reader's terms — e.g. "Could not load your mirrored collections". */
	title: string;
	/** Retries the failed query. Omit for an error the user cannot retry away. */
	onRetry?: () => void;
	className?: string;
}

/**
 * The one failed-query surface. Every admin section that loads over the network renders this on
 * error, so a failure always looks the same and always offers the same way out.
 */
export function QueryErrorAlert({ error, title, onRetry, className }: QueryErrorAlertProps) {
	return (
		<Alert variant="destructive" className={className}>
			<AlertCircleIcon />
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>{problemDetailOf(error)}</AlertDescription>
			{onRetry && (
				<AlertAction>
					<Button type="button" variant="outline" size="sm" onClick={onRetry}>
						Retry
					</Button>
				</AlertAction>
			)}
		</Alert>
	);
}
