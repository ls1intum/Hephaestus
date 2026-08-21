import { ChevronLeft, XIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useDetailDrawerLevel } from "@/components/core/detail-drawer/DetailDrawerStack";
import { Button } from "@/components/ui/button";
import {
	DrawerBody,
	DrawerDescription,
	DrawerFooter,
	DrawerHeader,
	DrawerTitle,
} from "@/components/ui/drawer";
import { cn } from "@/lib/utils";

export interface DetailDrawerPanelProps {
	title: ReactNode;
	description?: ReactNode;
	/** A decorative badge or area pill shown beside the title. */
	media?: ReactNode;
	/** Pinned below the scrolling body — the level's primary action belongs here. */
	footer?: ReactNode;
	className?: string;
	children: ReactNode;
}

/**
 * The chrome every detail drawer wears, at every depth: a pinned header, one scrolling body, and a
 * pinned footer for the action. Using it everywhere is what stops a stack of drawers from looking
 * like a stack of unrelated screens.
 *
 * The dismiss control is a back arrow below the top level and a close cross at the top, because
 * those levels do different things — one pops to the drawer behind it, the other returns to the page.
 */
export function DetailDrawerPanel({
	title,
	description,
	media,
	footer,
	className,
	children,
}: DetailDrawerPanelProps) {
	const { depth, close } = useDetailDrawerLevel();
	const nested = depth > 0;

	return (
		<>
			<DrawerHeader className="flex-row items-start gap-3">
				<Button variant="ghost" size="icon-sm" onClick={close} className="-ml-1 shrink-0">
					{nested ? <ChevronLeft /> : <XIcon />}
					<span className="sr-only">{nested ? "Back" : "Close"}</span>
				</Button>
				{media}
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle className="break-words">{title}</DrawerTitle>
					{description && (
						<DrawerDescription className="break-words">{description}</DrawerDescription>
					)}
				</div>
			</DrawerHeader>
			<DrawerBody className={cn("space-y-6", className)}>{children}</DrawerBody>
			{footer && <DrawerFooter>{footer}</DrawerFooter>}
		</>
	);
}
