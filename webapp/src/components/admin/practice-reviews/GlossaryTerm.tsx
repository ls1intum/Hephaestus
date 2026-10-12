import type { ReactNode } from "react";

import { cn } from "cn";
import { FOCUS_RING } from "@/components/common/focus";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

import { splitTerms, TRACE_TERMS, type TraceTerm } from "./trace-glossary";

export interface GlossaryTermProps {
	term: TraceTerm;
	/**
	 * `button` where the word stands in plain text, so a keyboard reaches the definition; `span`
	 * inside a control that is already a link or a button, where the definition is the pointer's
	 * bonus and the control keeps its one focus stop.
	 */
	as?: "button" | "span";
	children: ReactNode;
}

const TERM_CLASS =
	"cursor-help underline decoration-muted-foreground decoration-dotted underline-offset-2";

/** A word of the story with a dotted underline; its definition from the glossary shows on hover or focus. */
export function GlossaryTerm({ term, as = "button", children }: GlossaryTermProps) {
	const def = TRACE_TERMS[term];
	return (
		<Tooltip>
			<TooltipTrigger
				render={
					as === "button" ? (
						<button type="button" className={cn(TERM_CLASS, "rounded-sm", FOCUS_RING)} />
					) : (
						<span className={TERM_CLASS} />
					)
				}
			>
				{children}
			</TooltipTrigger>
			<TooltipContent className="max-w-xs">
				<span className="font-semibold">{def.label}</span> · {def.description}
			</TooltipContent>
		</Tooltip>
	);
}

/** A sentence with each glossary term in it explained the first time it comes. */
export function TermSentence({ text, as }: { text: string; as?: GlossaryTermProps["as"] }) {
	return (
		<>
			{splitTerms(text).map((part) =>
				typeof part === "string" ? (
					part
				) : (
					// A sentence marks each term once, so the term is the part's identity.
					<GlossaryTerm key={part.term} term={part.term} as={as}>
						{part.text}
					</GlossaryTerm>
				),
			)}
		</>
	);
}
