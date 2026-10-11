import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";

import { InlineLink } from "@/components/common/InlineLink";
import type { AgentPurpose } from "@/components/practice-vocabulary/agent-purpose-defs";

export interface PracticeScriptModelLinkProps {
	workspaceSlug: string;
	/** The purpose AI models opens and scrolls to. */
	purpose: AgentPurpose;
	children: ReactNode;
}

/** The way to a practice script's missing or failing model: its purpose on AI models. */
export function PracticeScriptModelLink({
	workspaceSlug,
	purpose,
	children,
}: PracticeScriptModelLinkProps) {
	return (
		<InlineLink
			render={
				<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} search={{ purpose }} />
			}
			className="inline-flex items-center gap-1 font-medium"
		>
			{children}
		</InlineLink>
	);
}
