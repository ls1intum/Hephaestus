import type { ReactNode } from "react";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { DrawerBody, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";

export interface PracticeFormLevelProps {
	creating: boolean;
	nested?: boolean;
	children: ReactNode;
}

/**
 * The practice editor as a drawer level, so changing a practice keeps the tree it belongs to on
 * screen.
 *
 * The level is guarded (`GUARDED_LEVEL_KINDS`): Escape, a press on the page and a swipe do not
 * reach it, because a form carries work those gestures would discard without asking. The header's
 * control and Cancel are both `DrawerClose`, which reports `close-press` and does close it.
 */
export function PracticeFormLevel({ creating, nested, children }: PracticeFormLevelProps) {
	return (
		<>
			<DetailDrawerHeader nested={nested}>
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle>{creating ? "Create practice" : "Edit practice"}</DrawerTitle>
					<DrawerDescription>
						{creating
							? "Define a way of working and choose how it is reviewed."
							: "Update this practice's review rules and developer guidance."}
					</DrawerDescription>
				</div>
			</DetailDrawerHeader>
			<DrawerBody>{children}</DrawerBody>
		</>
	);
}
