import { Sparkles, SquareArrowOutUpRight, SquarePen, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
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
	/** Trigger starting a fresh chat session */
	onNewChat?: () => void;
	/** Open the current chat in the full mentor route */
	onOpenFullChat?: () => void;
	/** Whether there are any messages in the current conversation */
	hasMessages?: boolean;
}

export function Copilot({
	children,
	defaultOpen = false,
	open: controlledOpen,
	onOpenChange,
	className,
	onNewChat,
	onOpenFullChat,
	hasMessages = false,
}: CopilotProps) {
	const [internalOpen, setInternalOpen] = useState(defaultOpen);

	// Use controlled state if provided, otherwise use internal state
	const isOpen = controlledOpen !== undefined ? controlledOpen : internalOpen;
	const setIsOpen = onOpenChange || setInternalOpen;

	// Handle trigger click
	const handleTriggerClick = () => {
		setIsOpen(!isOpen);
	};

	// Handle close via close button in content
	const handleClose = () => {
		setIsOpen(false);
	};

	const prevBodyStylesRef = useRef<{
		overflow: string;
		paddingRight: string;
		touchAction: string;
	} | null>(null);
	const lockBodyScroll = () => {
		if (prevBodyStylesRef.current) return;
		const body = document.body;
		prevBodyStylesRef.current = {
			overflow: body.style.overflow,
			paddingRight: body.style.paddingRight,
			touchAction: body.style.touchAction,
		};
		const scrollBarWidth = window.innerWidth - document.documentElement.clientWidth;
		if (scrollBarWidth > 0) body.style.paddingRight = `${scrollBarWidth}px`;
		body.style.overflow = "hidden";
		body.style.touchAction = "none";
	};
	const unlockBodyScroll = () => {
		const prev = prevBodyStylesRef.current;
		if (!prev) return;
		const body = document.body;
		body.style.overflow = prev.overflow;
		body.style.paddingRight = prev.paddingRight;
		body.style.touchAction = prev.touchAction;
		prevBodyStylesRef.current = null;
	};
	// biome-ignore lint/correctness/useExhaustiveDependencies: unlockBodyScroll stability handled by React Compiler
	useEffect(() => {
		if (!isOpen) unlockBodyScroll();
		return () => unlockBodyScroll();
	}, [isOpen]);

	// Handle open change with click outside detection
	const handleOpenChange = (open: boolean, eventDetails?: { reason?: string }) => {
		// Close on click outside but prevent default behavior
		if (!open && eventDetails?.reason === "outside-press") {
			setIsOpen(false);
			return;
		}
		setIsOpen(open);
	};

	return (
		<div className={cn("fixed bottom-6 right-6 z-50", className)}>
			<Popover open={isOpen} onOpenChange={handleOpenChange}>
				<PopoverTrigger
					render={
						<Button
							onClick={handleTriggerClick}
							className="size-16 rounded-full shadow-lg hover:shadow-xl transition-all duration-200 active:scale-95"
							size="icon"
							aria-label="Open Heph - AI Mentor"
						>
							<MentorIcon size={56} pad={8} />
						</Button>
					}
				/>
				<PopoverContent
					className="p-0 h-[calc(100dvh-10rem)] rounded-2xl overflow-hidden shadow-2xl border-1 overscroll-contain w-[calc(100vw-3rem)] sm:w-[28rem] md:w-[32rem] max-w-[32rem]"
					side="top"
					align="end"
					alignOffset={0}
					sideOffset={8}
					onPointerEnter={lockBodyScroll}
					onPointerLeave={unlockBodyScroll}
					onTouchStart={lockBodyScroll}
					onTouchEnd={unlockBodyScroll}
					onWheelCapture={(e) => {
						e.stopPropagation();
					}}
				>
					<div className="flex flex-col w-full h-full bg-background rounded-2xl overflow-hidden">
						<div className="flex items-center justify-between p-2 pl-4 border-b">
							<h3 className="text-sm font-medium text-muted-foreground flex items-center gap-2">
								<MentorIcon className="-mx-1.5" size={32} pad={4} />
								Heph{" "}
								<Badge variant="outline" className="text-muted-foreground">
									<Sparkles /> AI Mentor
								</Badge>
							</h3>
							<TooltipProvider delay={0}>
								<div className="flex items-center gap-1">
									{onOpenFullChat && (
										<Tooltip>
											<TooltipTrigger
												render={
													<Button
														variant="outline"
														size="icon"
														onClick={onOpenFullChat}
														aria-label="Open in mentor view"
														disabled={!hasMessages}
													>
														<SquareArrowOutUpRight />
													</Button>
												}
											/>
											<TooltipContent>Open in full screen</TooltipContent>
										</Tooltip>
									)}
									{onNewChat && (
										<Tooltip>
											<TooltipTrigger
												render={
													<Button
														variant="outline"
														size="icon"
														onClick={onNewChat}
														aria-label="Start new chat"
														disabled={!hasMessages}
													>
														<SquarePen />
													</Button>
												}
											/>
											<TooltipContent>New chat</TooltipContent>
										</Tooltip>
									)}
									<Tooltip>
										<TooltipTrigger
											render={
												<Button
													variant="outline"
													size="icon"
													onClick={handleClose}
													aria-label="Close Heph - AI Mentor"
												>
													<X />
												</Button>
											}
										/>
										<TooltipContent>Close</TooltipContent>
									</Tooltip>
								</div>
							</TooltipProvider>
						</div>
						<div className="flex-1 min-h-0 overscroll-contain">{children}</div>
					</div>
				</PopoverContent>
			</Popover>
		</div>
	);
}
