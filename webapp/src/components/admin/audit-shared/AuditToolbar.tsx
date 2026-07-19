import { XIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";

export interface AuditToolbarProps {
	/** The facet controls, left-aligned. */
	children: ReactNode;
	/** Whether anything is filtered — drives the Reset affordance. */
	hasFilter: boolean;
	onReset: () => void;
	/** Right-aligned actions (e.g. Export CSV). */
	actions?: ReactNode;
}

/**
 * The filter row shared by both audit surfaces: dashed facet triggers on the left, a Reset that
 * appears only once something is filtered, and page actions pushed right. Lifted out of the pages so
 * the two trails cannot drift into two different filter idioms.
 */
export function AuditToolbar({ children, hasFilter, onReset, actions }: AuditToolbarProps) {
	return (
		<div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
			{children}
			{hasFilter && (
				<Button variant="ghost" size="sm" className="h-8 px-2 lg:px-3" onClick={onReset}>
					Reset
					<XIcon aria-hidden />
				</Button>
			)}
			{/* ml-auto only from sm up: on one wrapped mobile column it would strand the actions on a
			    line of their own, flush right against nothing. */}
			{actions && <div className="flex items-center gap-2 sm:ml-auto">{actions}</div>}
		</div>
	);
}
