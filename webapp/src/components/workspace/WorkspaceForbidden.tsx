import { useNavigate } from "@tanstack/react-router";
import { Home, ShieldX } from "lucide-react";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

export interface WorkspaceForbiddenProps {
	/** The slug that the user cannot access (optional, for display purposes) */
	slug?: string;
}

/**
 * Empty state component displayed when access to a workspace is forbidden (403).
 * Shows a friendly message explaining the user lacks permission and provides recovery options.
 *
 * Accessibility:
 * - Uses role="alert" to announce to screen readers
 * - Auto-focuses the container for keyboard navigation
 * - Provides clear action buttons with descriptive labels
 */
export function WorkspaceForbidden({ slug }: WorkspaceForbiddenProps) {
	const navigate = useNavigate();
	const containerRef = useRef<HTMLDivElement>(null);

	// Focus the container when mounted for screen reader announcement
	useEffect(() => {
		containerRef.current?.focus();
	}, []);

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
						<ShieldX aria-hidden="true" />
					</EmptyMedia>
					<EmptyTitle>Access denied</EmptyTitle>
					<EmptyDescription>
						{slug ? (
							<>
								You don&apos;t have permission to access the workspace{" "}
								<strong>&quot;{slug}&quot;</strong>. Contact a workspace administrator if you
								believe this is a mistake.
							</>
						) : (
							<>
								You don&apos;t have permission to access this workspace. Contact a workspace
								administrator if you believe this is a mistake.
							</>
						)}
					</EmptyDescription>
				</EmptyHeader>
			</div>
			<Button onClick={() => navigate({ to: "/" })}>
				<Home className="mr-2 size-4" aria-hidden="true" />
				Go to home
			</Button>
		</Empty>
	);
}
