import { useNavigate } from "@tanstack/react-router";
import { ShieldX } from "lucide-react";
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
 * Shows a friendly message explaining the user lacks permission.
 */
export function WorkspaceForbidden({ slug }: WorkspaceForbiddenProps) {
	const navigate = useNavigate();

	const handleGoHome = () => {
		navigate({ to: "/" });
	};

	return (
		<Empty>
			<EmptyHeader>
				<EmptyMedia variant="icon">
					<ShieldX />
				</EmptyMedia>
				<EmptyTitle>Access denied</EmptyTitle>
				<EmptyDescription>
					{slug ? (
						<>
							You don&apos;t have permission to access the workspace{" "}
							<strong>&quot;{slug}&quot;</strong>. Contact a workspace administrator if you believe
							this is a mistake.
						</>
					) : (
						<>
							You don&apos;t have permission to access this workspace. Contact a workspace
							administrator if you believe this is a mistake.
						</>
					)}
				</EmptyDescription>
			</EmptyHeader>
			<Button onClick={handleGoHome}>Go to home</Button>
		</Empty>
	);
}
