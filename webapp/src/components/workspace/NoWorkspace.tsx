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
 * Accessibility:
 * - Uses role="status" for non-critical information
 * - Auto-focuses the container for keyboard navigation
 */
export function NoWorkspace() {
	const containerRef = useRef<HTMLDivElement>(null);

	// Focus the container when mounted for screen reader announcement
	useEffect(() => {
		containerRef.current?.focus();
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
