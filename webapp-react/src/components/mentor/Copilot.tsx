import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { Sparkles, X } from "lucide-react";
import { memo, useCallback, useState } from "react";
import { MentorIcon } from "./MentorIcon";

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
						aria-label="Open Heph - AI Mentor"
					>
						<MentorIcon className="size-12" />
					</Button>
				</PopoverTrigger>

				<PopoverContent
					className="p-0 w-[calc(100vw-3rem)] max-w-lg h-[calc(100dvh-10rem)] rounded-2xl overflow-hidden shadow-2xl border-1 md:w-full"
					side="top"
					align="end"
					alignOffset={0}
					sideOffset={8}
					onInteractOutside={(e) => {
						e.preventDefault();
						setIsOpen(false);
					}}
				>
					<div className="flex flex-col w-full h-full bg-background rounded-2xl overflow-hidden">
						{/* Header with close button */}
						<div className="flex items-center justify-between p-2 pl-4 border-b">
							<h3 className="text-sm font-medium text-muted-foreground flex items-center gap-2">
								<MentorIcon className="size-8" />
								Heph{" "}
								<Badge variant="outline" className="text-muted-foreground">
									<Sparkles /> AI Mentor
								</Badge>
							</h3>
							<Button
								variant="ghost"
								size="icon"
								onClick={handleClose}
								aria-label="Close Heph - AI Mentor"
							>
								<X />
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
