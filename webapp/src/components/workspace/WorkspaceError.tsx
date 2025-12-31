import { useNavigate, useRouter } from "@tanstack/react-router";
import { AlertTriangle, Home, RefreshCw } from "lucide-react";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

export interface WorkspaceErrorProps {
	/** The error that occurred */
	error: Error;
	/** Optional reset function provided by error boundaries */
	reset?: () => void;
}

/**
 * Determines if an error is likely network-related and thus potentially transient.
 * Uses error.name (more reliable) with message fallback for fetch-based errors.
 */
function isNetworkError(error: Error): boolean {
	// Check error.name first - more reliable than message parsing
	if (error.name === "TypeError" || error.name === "NetworkError") {
		return true;
	}

	// Fallback: check common fetch/network error patterns in message
	// Using case-insensitive matching for robustness across browsers
	const networkPatterns = /\b(fetch|network|timeout|abort|connection|offline)\b/i;
	return networkPatterns.test(error.message);
}

/**
 * Generic error state component for unexpected workspace errors.
 * Provides retry functionality and clear messaging for recoverable errors.
 *
 * Accessibility (WCAG 2.2):
 * - Uses role="alert" with aria-live="assertive" for immediate screen reader announcement
 * - Auto-focuses container after render for keyboard navigation (via requestAnimationFrame)
 * - Icons marked aria-hidden to prevent redundant announcements
 * - aria-atomic ensures the entire message is read as a unit
 */
export function WorkspaceError({ error, reset }: WorkspaceErrorProps) {
	const navigate = useNavigate();
	const router = useRouter();
	const containerRef = useRef<HTMLDivElement>(null);

	// Focus the container after render for screen reader announcement
	// Using requestAnimationFrame ensures the DOM is fully painted before focus
	useEffect(() => {
		const frameId = requestAnimationFrame(() => {
			containerRef.current?.focus();
		});
		return () => cancelAnimationFrame(frameId);
	}, []);

	const handleRetry = () => {
		if (reset) {
			reset();
		} else {
			router.invalidate();
		}
	};

	const handleGoHome = () => {
		navigate({ to: "/" });
	};

	const showNetworkMessage = isNetworkError(error);

	return (
		<Empty>
			{/* Accessible error announcement region */}
			<div
				ref={containerRef}
				role="alert"
				aria-live="assertive"
				aria-atomic="true"
				tabIndex={-1}
				className="outline-none"
			>
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<AlertTriangle aria-hidden="true" />
					</EmptyMedia>
					<EmptyTitle>Something went wrong</EmptyTitle>
					<EmptyDescription>
						{showNetworkMessage ? (
							<>
								We couldn&apos;t load this workspace. This might be a temporary issue — please try
								again.
							</>
						) : (
							<>
								An unexpected error occurred while loading this workspace. If the problem persists,
								please contact support.
							</>
						)}
					</EmptyDescription>
				</EmptyHeader>
			</div>
			<div className="flex flex-wrap items-center justify-center gap-3">
				<Button variant="outline" onClick={handleRetry}>
					<RefreshCw className="mr-2 size-4" aria-hidden="true" />
					Try again
				</Button>
				<Button onClick={handleGoHome}>
					<Home className="mr-2 size-4" aria-hidden="true" />
					Go to home
				</Button>
			</div>
			{/* Debug information in development */}
			{process.env.NODE_ENV === "development" && (
				<details className="mt-4 max-w-md text-left text-xs text-muted-foreground">
					<summary className="cursor-pointer hover:text-foreground">Error details</summary>
					<pre className="mt-2 overflow-auto rounded bg-muted p-2">
						{error.name}: {error.message}
						{error.stack && `\n\n${error.stack}`}
					</pre>
				</details>
			)}
		</Empty>
	);
}
