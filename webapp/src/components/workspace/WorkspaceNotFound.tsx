import { useNavigate } from "@tanstack/react-router";
import { SearchX } from "lucide-react";
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
}

/**
 * Empty state component displayed when a workspace is not found (404).
 * Shows a friendly message and provides navigation options.
 */
export function WorkspaceNotFound({ slug }: WorkspaceNotFoundProps) {
	const navigate = useNavigate();

	const handleGoHome = () => {
		navigate({ to: "/" });
	};

	return (
		<Empty>
			<EmptyHeader>
				<EmptyMedia variant="icon">
					<SearchX />
				</EmptyMedia>
				<EmptyTitle>Workspace not found</EmptyTitle>
				<EmptyDescription>
					{slug ? (
						<>
							The workspace <strong>&quot;{slug}&quot;</strong> doesn&apos;t exist or may have been
							deleted.
						</>
					) : (
						<>The workspace you&apos;re looking for doesn&apos;t exist or may have been deleted.</>
					)}
				</EmptyDescription>
			</EmptyHeader>
			<Button onClick={handleGoHome}>Go to home</Button>
		</Empty>
	);
}
