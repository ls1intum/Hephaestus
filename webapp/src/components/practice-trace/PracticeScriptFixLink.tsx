import { Link } from "@tanstack/react-router";
import { ArrowRightIcon } from "lucide-react";

import { practiceFormLevel } from "@/components/admin/practices/practice-search";
import { InlineLink } from "@/components/common/InlineLink";
import { detailSearch } from "@/components/layout/detail-drawer/detail-stack";
import {
	PRACTICE_SCRIPT_GUIDE_DEFS,
	type PracticeScriptFix,
	practiceScriptGuideUrl,
} from "@/components/practice-vocabulary/practice-script-run-status-defs";

export interface PracticeScriptFixLinkProps {
	workspaceSlug: string;
	/** The practice whose script the editor opens. */
	practiceSlug: string;
	/**
	 * The practice's name, for a screen reader, where one list repeats the same link for several
	 * practices. Absent where the link sits in the practice's own sentence.
	 */
	practiceName?: string;
	fix: PracticeScriptFix;
}

/**
 * The way to what would help a practice script next time: the script itself, or
 * the guide when nothing in the workspace fixes it. An arrow, like the trace's other ways onward,
 * tells it from the result beside it; the guide wears the outbound icon instead.
 */
export function PracticeScriptFixLink({
	workspaceSlug,
	practiceSlug,
	practiceName,
	fix,
}: PracticeScriptFixLinkProps) {
	// After the visible words, so speech control still matches them (WCAG SC 2.5.3).
	const context =
		practiceName === undefined ? null : (
			<>
				{" "}
				<span className="sr-only">for {practiceName}</span>
			</>
		);
	if (fix.kind === "GUIDE") {
		return (
			<InlineLink href={practiceScriptGuideUrl(fix.guide)} external className="font-medium">
				{PRACTICE_SCRIPT_GUIDE_DEFS[fix.guide].label}
				{context}
			</InlineLink>
		);
	}
	return (
		<InlineLink
			render={
				<Link
					to="/w/$workspaceSlug/admin/practices"
					params={{ workspaceSlug }}
					search={detailSearch(practiceFormLevel(practiceSlug))}
				/>
			}
			className="inline-flex items-center gap-1 font-medium"
		>
			Edit the script{context}
			<ArrowRightIcon className="size-3 shrink-0" aria-hidden />
		</InlineLink>
	);
}
