import { ChevronLeft, XIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { DrawerClose, DrawerHeader } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";

export interface DetailDrawerHeaderProps {
	/** True below the top level, where dismissing returns to the drawer behind rather than the page. */
	nested?: boolean;
	className?: string;
	children: ReactNode;
}

/**
 * The rest of a panel is the primitive's own `DrawerBody`/`DrawerFooter`/`DrawerTitle`, so panels
 * compose rather than fill a fixed set of holes; `nested` is the only prop a Storybook control could
 * drive, so the compound shape costs nothing there.
 *
 * The dismiss is a `DrawerClose`, so it closes through the same path as Escape, an outside press and
 * a swipe rather than a fourth one that can drift.
 *
 * The content row wraps. A drawer is the full viewport at 320px and the dismiss, the padding and a
 * leading chip already spend 40% of it, so a third item beside the title leaves it a column too
 * narrow to read — put anything that is itself text below the title rather than next to it.
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
			<div className="flex min-w-0 flex-1 flex-wrap items-start gap-x-3 gap-y-2">{children}</div>
		</DrawerHeader>
	);
}
