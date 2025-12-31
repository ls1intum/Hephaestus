import { Folders } from "lucide-react";
import { useEffect, useRef } from "react";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

/**
 * Empty state component displayed when a user has no workspace memberships.
 * Shown after authentication when the user hasn't been added to any workspace.
 *
 * Accessibility (WCAG 2.2):
 * - Uses role="status" with aria-live="polite" for non-disruptive screen reader announcement
 * - Auto-focuses container after render for keyboard navigation (via requestAnimationFrame)
 * - Icons marked aria-hidden to prevent redundant announcements
 */
export function NoWorkspace() {
	const containerRef = useRef<HTMLDivElement>(null);

	// Focus the container after render for screen reader announcement
	// Using requestAnimationFrame ensures the DOM is fully painted before focus
	useEffect(() => {
		const frameId = requestAnimationFrame(() => {
			containerRef.current?.focus();
		});
		return () => cancelAnimationFrame(frameId);
	}, []);

	return (
		<Empty>
			{/* Accessible status announcement region */}
			<div
				ref={containerRef}
				role="status"
				aria-live="polite"
				tabIndex={-1}
				className="outline-none"
			>
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Folders aria-hidden="true" />
					</EmptyMedia>
					<EmptyTitle>No workspace</EmptyTitle>
					<EmptyDescription>
						You&apos;re not a member of any workspace yet. Contact your administrator to request
						access to a workspace.
					</EmptyDescription>
				</EmptyHeader>
			</div>
		</Empty>
	);
}
