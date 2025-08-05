import { Button } from "@/components/ui/button";
import { initialArtifactData, useArtifact } from "@/hooks/use-artifact";
import { X } from "lucide-react";
import { memo } from "react";

/**
 * ArtifactCloseButton provides a close button for artifacts.
 * Smart component that directly manages artifact visibility state.
 *
 * Behavior:
 * - If artifact is streaming: hides the artifact but keeps it in memory
 * - If artifact is idle: completely resets the artifact to initial state
 */
function PureArtifactCloseButton() {
	const { setArtifact } = useArtifact();

	return (
		<Button
			data-testid="artifact-close-button"
			className="dark:border-primary/10 dark:hover:bg-primary/10"
			variant="outline"
			size="icon"
			onClick={() => {
				setArtifact((currentArtifact) =>
					currentArtifact.status === "streaming"
						? {
								...currentArtifact,
								isVisible: false,
							}
						: { ...initialArtifactData, status: "idle" },
				);
			}}
		>
			<X className="size-6" strokeWidth={1.5} />
		</Button>
	);
}

export const ArtifactCloseButton = memo(PureArtifactCloseButton, () => true);
