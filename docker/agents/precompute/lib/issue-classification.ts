/**
 * What kind of card an issue is, read the same way by every issue practice: a body that adds nothing
 * to the title, whether the issue is typed as a deliverable, and whether it is an umbrella card.
 * Inputs are the already-lowercased type and labels.
 */
export function classifyIssue(title: string, body: string, issueType: string, labels: string[]) {
	const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
	const titleNorm = norm(title);
	const bodyNorm = norm(body);
	const titleEcho =
		bodyNorm.length > 0 &&
		(bodyNorm === titleNorm || titleNorm.includes(bodyNorm) || bodyNorm.includes(titleNorm));
	const emptyOrTitleEcho = body.length < 25 || titleEcho;
	const deliverableType =
		/\b(user ?story|story|bug|defect|feature|enhancement|task|chore|requirement|artifact|epic|spike)\b/;
	const hasDeliverableType =
		deliverableType.test(issueType) || labels.some((l) => deliverableType.test(l));
	const looksUmbrella =
		labels.some((l) => /\b(epic|umbrella|meta|initiative|requirement)\b/.test(l)) ||
		/\b(epic|umbrella|initiative)\b/i.test(title);
	return { emptyOrTitleEcho, hasDeliverableType, looksUmbrella };
}
