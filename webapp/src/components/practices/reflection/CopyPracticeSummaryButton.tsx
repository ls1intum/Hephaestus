import { Check, Copy } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import type { PracticeReportCard, PracticeReportItem } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { copyHtmlAndText } from "@/lib/clipboard";

const STANDING_LABEL: Record<PracticeReportCard["standing"], string> = {
	STRENGTH: "Strength",
	DEVELOPING: "Developing",
	MIXED: "Mixed",
};

function itemLine(item: PracticeReportItem): string {
	const kind = item.artifactType === "PULL_REQUEST" ? "PR" : "Issue";
	const ref = item.locator ?? `${kind} #${item.artifactId}`;
	const guidance = item.guidance ? ` — ${item.guidance}` : "";
	return `${item.title} (${ref})${guidance}`;
}

function buildMarkdown(practices: PracticeReportCard[]): string {
	const lines: string[] = ["# My practice summary", ""];
	for (const practice of practices) {
		lines.push(`## ${practice.name} — ${STANDING_LABEL[practice.standing]}`);
		if (practice.strengths.length > 0) {
			lines.push("", "**What I'm doing well**");
			for (const item of practice.strengths) lines.push(`- ${itemLine(item)}`);
		}
		if (practice.toWorkOn.length > 0) {
			lines.push("", "**To work on**");
			for (const item of practice.toWorkOn) lines.push(`- ${itemLine(item)}`);
		}
		lines.push("");
	}
	return lines.join("\n").trimEnd();
}

function escapeHtml(value: string): string {
	return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function buildHtml(practices: PracticeReportCard[]): string {
	const sections = practices.map((practice) => {
		const strengths =
			practice.strengths.length > 0
				? `<p><strong>What I'm doing well</strong></p><ul>${practice.strengths
						.map((item) => `<li>${escapeHtml(itemLine(item))}</li>`)
						.join("")}</ul>`
				: "";
		const toWorkOn =
			practice.toWorkOn.length > 0
				? `<p><strong>To work on</strong></p><ul>${practice.toWorkOn
						.map((item) => `<li>${escapeHtml(itemLine(item))}</li>`)
						.join("")}</ul>`
				: "";
		return `<h3>${escapeHtml(practice.name)} — ${STANDING_LABEL[practice.standing]}</h3>${strengths}${toWorkOn}`;
	});
	return `<h2>My practice summary</h2>${sections.join("")}`;
}

export interface CopyPracticeSummaryButtonProps {
	practices: PracticeReportCard[];
}

/**
 * Copies the developer's OWN practice reflection as a markdown (+ HTML) digest. Never comparative —
 * it only ever describes the current developer's own practices.
 */
export function CopyPracticeSummaryButton({ practices }: CopyPracticeSummaryButtonProps) {
	const [copied, setCopied] = useState(false);
	const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const disabled = practices.length === 0;

	const handleCopy = async () => {
		if (disabled) return;
		const succeeded = await copyHtmlAndText(buildHtml(practices), buildMarkdown(practices));
		if (!succeeded) {
			// Don't claim "Copied" when the browser rejected the write (e.g. permission denied).
			toast.error("Couldn't copy to the clipboard");
			return;
		}
		setCopied(true);
		// The toast doubles as the screen-reader announcement — the button's visual flip to
		// "Copied" is a plain DOM change, not an ARIA live region.
		toast.success("Copied to clipboard");
		if (timerRef.current !== null) clearTimeout(timerRef.current);
		timerRef.current = setTimeout(() => {
			setCopied(false);
			timerRef.current = null;
		}, 2000);
	};

	useEffect(() => {
		return () => {
			if (timerRef.current !== null) clearTimeout(timerRef.current);
		};
	}, []);

	return (
		<Button variant="outline" size="sm" onClick={handleCopy} disabled={disabled}>
			{copied ? (
				<Check className="text-provider-done-foreground" aria-hidden="true" />
			) : (
				<Copy aria-hidden="true" />
			)}
			{copied ? "Copied" : "Copy my practice summary"}
		</Button>
	);
}
