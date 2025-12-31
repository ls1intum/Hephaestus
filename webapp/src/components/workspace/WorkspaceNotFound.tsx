import { useNavigate, useRouter } from "@tanstack/react-router";
import { Home, RefreshCw, SearchX } from "lucide-react";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

export interface WorkspaceNotFoundProps {
	/** The slug that was not found (optional, for display purposes) */
	slug?: string;
	/** Whether to show a retry button for transient errors */
	showRetry?: boolean;
}

/**
 * Empty state component displayed when a workspace is not found (404).
 * Shows a friendly message and provides navigation options.
 *
 * Accessibility:
 * - Uses role="alert" to announce to screen readers
 * - Auto-focuses the container for keyboard navigation
 * - Provides clear action buttons with descriptive labels
 */
export function WorkspaceNotFound({ slug, showRetry = false }: WorkspaceNotFoundProps) {
	const navigate = useNavigate();
	const router = useRouter();
	const containerRef = useRef<HTMLDivElement>(null);

	// Focus the container when mounted for screen reader announcement
	useEffect(() => {
		containerRef.current?.focus();
	}, []);

	const handleRetry = () => {
		router.invalidate();
	};

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
						<SearchX aria-hidden="true" />
					</EmptyMedia>
					<EmptyTitle>Workspace not found</EmptyTitle>
					<EmptyDescription>
						{slug ? (
							<>
								The workspace <strong>&quot;{slug}&quot;</strong> doesn&apos;t exist or may have
								been deleted.
							</>
						) : (
							<>
								The workspace you&apos;re looking for doesn&apos;t exist or may have been deleted.
							</>
						)}
					</EmptyDescription>
				</EmptyHeader>
			</div>
			<div className="flex flex-wrap items-center justify-center gap-3">
				{showRetry && (
					<Button variant="outline" onClick={handleRetry}>
						<RefreshCw className="mr-2 size-4" aria-hidden="true" />
						Try again
					</Button>
				)}
				<Button onClick={() => navigate({ to: "/" })}>
					<Home className="mr-2 size-4" aria-hidden="true" />
					Go to home
				</Button>
			</div>
		</Empty>
	);
}
