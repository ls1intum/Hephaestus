import type { PracticeEvidenceDeclaration } from "@/api/types.gen";

const SOURCE_LABELS: Record<string, string> = {
	"scm.pull-request.core": "Pull request details",
	"scm.pull-request.diff": "Code changes",
	"scm.pull-request.comments": "Inline review comments",
	"scm.repository.tree": "Repository files",
	"scm.issue.core": "Issue details",
	"scm.issue.comments": "Issue comments",
	"slack.conversation.thread": "Slack thread",
	"scm.linked-work-items": "Linked work items",
	"scm.review-threads": "Review threads and decisions",
	"scm.general-review-comments": "General review comments",
	"workspace.project-inventory": "Related workspace work",
	"outline.documents": "Referenced Outline documents",
};

export function evidenceSourceLabel(sourceKind: string) {
	return SOURCE_LABELS[sourceKind] ?? sourceKind;
}

export function evidenceQualityLabel(requirement: PracticeEvidenceDeclaration["required"][number]) {
	if (requirement.completeness === "COMPLETE" && requirement.freshness === "CURRENT") {
		return "Complete and current";
	}
	if (requirement.completeness === "COMPLETE") return "Complete; any age";
	if (requirement.freshness === "CURRENT") return "Current; partial allowed";
	return "Any available quality";
}
