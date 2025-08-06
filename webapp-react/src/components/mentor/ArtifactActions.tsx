import { Button } from "@/components/ui/button";
import {
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { type ReactNode, memo } from "react";

export interface ArtifactAction {
	/** Unique identifier for the action */
	id: string;
	/** Icon to display in the button */
	icon: ReactNode;
	/** Optional text label */
	label?: string;
	/** Tooltip description */
	description: string;
	/** Whether the action is currently disabled */
	disabled?: boolean;
	/** Handler for action click */
	onClick: () => void;
}

interface ArtifactActionsProps {
	/** Array of available actions */
	actions: ArtifactAction[];
	/** Whether any action is currently loading */
	isLoading?: boolean;
	/** Whether the artifact is currently streaming */
	isStreaming?: boolean;
	/** Optional CSS class name */
	className?: string;
}

function PureArtifactActions({
	actions,
	isLoading = false,
	isStreaming = false,
	className,
}: ArtifactActionsProps) {
	if (actions.length === 0) {
		return null;
	}

	return (
		<div className={cn("flex flex-row gap-1", className)}>
			{actions.map((action) => (
				<Tooltip key={action.id}>
					<TooltipTrigger asChild>
						<Button
							variant="outline"
							className={cn("dark:border-primary/10 dark:hover:bg-primary/10", {
								"p-2": !action.label,
								"py-1.5 px-2": action.label,
							})}
							size={action.label ? "default" : "icon"}
							onClick={action.onClick}
							disabled={isLoading || isStreaming || action.disabled}
						>
							{action.icon}
							{action.label}
						</Button>
					</TooltipTrigger>
					<TooltipContent>{action.description}</TooltipContent>
				</Tooltip>
			))}
		</div>
	);
}

export const ArtifactActions = memo(
	PureArtifactActions,
	(prevProps, nextProps) => {
		if (prevProps.isLoading !== nextProps.isLoading) return false;
		if (prevProps.isStreaming !== nextProps.isStreaming) return false;
		if (prevProps.actions.length !== nextProps.actions.length) return false;

		// Check if any action properties changed
		for (let i = 0; i < prevProps.actions.length; i++) {
			const prevAction = prevProps.actions[i];
			const nextAction = nextProps.actions[i];

			if (prevAction.id !== nextAction.id) return false;
			if (prevAction.disabled !== nextAction.disabled) return false;
			if (prevAction.label !== nextAction.label) return false;
			if (prevAction.description !== nextAction.description) return false;
		}

		return true;
	},
);
