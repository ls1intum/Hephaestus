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
 * Accessibility (WCAG 2.2):
 * - Uses role="alert" with aria-live="assertive" for immediate screen reader announcement
 * - Auto-focuses container after render for keyboard navigation (via requestAnimationFrame)
 * - Icons marked aria-hidden to prevent redundant announcements
 * - Long slugs are truncated to prevent layout breakage
 */
export function WorkspaceForbidden({ slug }: WorkspaceForbiddenProps) {
	const navigate = useNavigate();
	const containerRef = useRef<HTMLDivElement>(null);

	// Focus the container after render for screen reader announcement
	// Using requestAnimationFrame ensures the DOM is fully painted before focus
	useEffect(() => {
		const frameId = requestAnimationFrame(() => {
			containerRef.current?.focus();
		});
		return () => cancelAnimationFrame(frameId);
	}, []);

	// Truncate extremely long slugs to prevent layout issues
	const displaySlug = slug && slug.length > 50 ? `${slug.slice(0, 47)}...` : slug;

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
						{displaySlug ? (
							<>
								You don&apos;t have permission to access the workspace{" "}
								<strong className="break-all" title={slug}>
									&quot;{displaySlug}&quot;
								</strong>
								. Contact a workspace administrator if you believe this is a mistake.
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
