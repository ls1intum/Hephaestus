import { ChevronLeft, XIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { DrawerClose, DrawerHeader } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";

export interface DetailDrawerHeaderProps {
	/** True below the top level, where dismissing returns to the drawer behind rather than the page. */
	nested?: boolean;
	className?: string;
	/** A `DrawerTitle`, usually beside a decorative pill. `DrawerDescription` is optional. */
	children: ReactNode;
}

/**
 * The one piece of chrome every detail drawer shares: a dismiss control, then whatever names the
 * level. Everything else a panel needs — `DrawerBody`, `DrawerFooter`, `DrawerTitle` — is the
 * primitive's own, so a panel composes rather than fills in a fixed set of holes.
 *
 * Storybook cost of the compound shape is close to zero here: the only prop with an explorable
 * control is `nested`, and the alternative — `title`/`description`/`media`/`footer` props — would
 * have published three ReactNode rows that no control can drive.
 *
 * The dismiss is a `DrawerClose`, not a `Button` wired to a callback, so it closes through the same
 * path as Escape, an outside press and a rightward swipe instead of a fourth one that can drift.
 */
export function DetailDrawerHeader({
	nested = false,
	className,
	children,
}: DetailDrawerHeaderProps) {
	return (
		<DrawerHeader className={cn("flex-row items-start gap-3", className)}>
			<DrawerClose render={<Button variant="ghost" size="icon-sm" className="-ml-2 shrink-0" />}>
				{nested ? <ChevronLeft /> : <XIcon />}
				<span className="sr-only">{nested ? "Back" : "Close"}</span>
			</DrawerClose>
			<div className="flex min-w-0 flex-1 items-start gap-3">{children}</div>
		</DrawerHeader>
	);
}
