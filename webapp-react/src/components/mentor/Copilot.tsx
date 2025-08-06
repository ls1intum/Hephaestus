import { Button } from "@/components/ui/button";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { BotMessageSquare, X } from "lucide-react";
import { memo, useCallback, useState } from "react";

export interface CopilotProps {
	/** Content to display in the popover (typically Chat component) */
	children: React.ReactNode;
	/** Whether the widget is initially open */
	defaultOpen?: boolean;
	/** Controlled open state */
	open?: boolean;
	/** Handler for open state changes */
	onOpenChange?: (open: boolean) => void;
	/** Optional CSS class name for the container */
	className?: string;
}

function PureCopilot({
	children,
	defaultOpen = false,
	open: controlledOpen,
	onOpenChange,
	className,
}: CopilotProps) {
	const [internalOpen, setInternalOpen] = useState(defaultOpen);

	// Use controlled state if provided, otherwise use internal state
	const isOpen = controlledOpen !== undefined ? controlledOpen : internalOpen;
	const setIsOpen = onOpenChange || setInternalOpen;

	// Handle trigger click
	const handleTriggerClick = useCallback(() => {
		setIsOpen(!isOpen);
	}, [isOpen, setIsOpen]);

	// Handle close via close button in content
	const handleClose = useCallback(() => {
		setIsOpen(false);
	}, [setIsOpen]);

	return (
		<div className={cn("fixed bottom-6 right-6 z-50", className)}>
			<Popover open={isOpen} onOpenChange={setIsOpen}>
				<PopoverTrigger asChild>
					<Button
						onClick={handleTriggerClick}
						className="size-16 rounded-full shadow-lg hover:shadow-xl transition-all duration-200 active:scale-95"
						size="icon"
						aria-label="Open AI Assistant"
					>
						<BotMessageSquare className="size-8" />
					</Button>
				</PopoverTrigger>

				<PopoverContent
					className="p-0 w-96 h-[600px] rounded-2xl overflow-hidden shadow-2xl border-0"
					side="top"
					align="end"
					sideOffset={8}
					onInteractOutside={(e) => {
						e.preventDefault();
						setIsOpen(false);
					}}
				>
					<div className="flex flex-col w-full h-full bg-background rounded-2xl overflow-hidden">
						{/* Header with close button */}
						<div className="flex items-center justify-between p-3 border-b">
							<h3 className="text-sm font-medium text-muted-foreground">
								AI Assistant
							</h3>
							<Button
								variant="ghost"
								size="icon"
								className="h-6 w-6 rounded-sm opacity-70 hover:opacity-100 focus:opacity-100"
								onClick={handleClose}
								aria-label="Close AI Assistant"
							>
								<X className="h-4 w-4" />
							</Button>
						</div>

						{/* Chat content container */}
						<div className="flex-1 min-h-0">{children}</div>
					</div>
				</PopoverContent>
			</Popover>
		</div>
	);
}

export const Copilot = memo(PureCopilot);
