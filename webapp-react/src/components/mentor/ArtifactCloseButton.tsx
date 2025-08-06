import { Button } from "@/components/ui/button";
import { X } from "lucide-react";
import { memo } from "react";

export interface ArtifactCloseButtonProps {
	/** Handler for closing the artifact */
	onClose: () => void;
}

/**
 * ArtifactCloseButton provides a close button for artifacts.
 * Always requires an explicit onClose handler to be provided.
 */
function PureArtifactCloseButton({ onClose }: ArtifactCloseButtonProps) {
	return (
		<Button
			data-testid="artifact-close-button"
			className="dark:border-primary/10 dark:hover:bg-primary/10"
			variant="outline"
			size="icon"
			onClick={onClose}
		>
			<X className="size-6" strokeWidth={1.5} />
		</Button>
	);
}

export const ArtifactCloseButton = memo(
	PureArtifactCloseButton,
	(prevProps, nextProps) => {
		return prevProps.onClose === nextProps.onClose;
	},
);
