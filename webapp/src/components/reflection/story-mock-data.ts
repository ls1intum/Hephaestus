import type { ReflectionFeedback } from "@/api/types.gen";

/**
 * Typed as the generated view with real `Date`s, because these fixtures are handed straight to
 * props: the screen takes its data that way, and the client's response transformer has already
 * revived the wire's ISO strings by the time a route passes them down.
 *
 * <p>The bodies are written to the lane's own rules, so a story is also a worked example of them:
 * evidence is a set of work rather than a quoted line, the lesson is about a way of working rather
 * than about one edit, every one ends with a habit to try next, and none of them says anything
 * about the developer.
 */

const testsWithTheChange: ReflectionFeedback = {
	id: "1f7a4b6e-1b6c-4c0f-9c2e-0a1b2c3d4e5f",
	headline: "Tests are arriving one commit late",
	body: [
		"On your last five pull requests, three shipped a new branch of logic with no test beside it — the",
		"tax-exempt path in `InvoiceTotals`, the refund rounding in `RefundCalc`, and the retry backoff in",
		"`PaymentRetry`. In all three the test landed, but only after a reviewer asked for it, which is a",
		"round trip that cost a day each time.",
		"",
		"The pattern is in the ordering rather than the intent: the behaviour gets written first and the",
		"test gets remembered at review.",
		"",
		"**Try next:** on your next change that adds a branch, write the one assertion that tells the new",
		"branch apart before you write the branch. If that assertion is hard to state, that is the signal",
		"the branch is doing more than one thing.",
	].join("\n"),
	practiceSlug: "ships-tests-with-the-change",
	practiceName: "Ship the test with the change",
	areaSlug: "testing",
	areaName: "Testing",
	whyItMatters:
		"Code without a test is a promise nobody can check, and it is one careless refactor away from quietly breaking. Adding the test alongside the logic locks in the behaviour you just built, which matters most when you are fixing a bug.",
	whatGoodLooksLike:
		"New logic arrives with a test beside it, and a bug fix comes with a test that fails on the old code and passes on the new, proving the behaviour is what you say it is.",
	evidence: [
		{
			artifactKind: "scm.pull_request",
			artifactId: 4301,
			observedAt: new Date("2026-08-12T09:15:00Z"),
			title: "New tax-exempt branch ships without a test",
		},
		{
			artifactKind: "scm.pull_request",
			artifactId: 4188,
			observedAt: new Date("2026-08-05T14:02:00Z"),
			title: "Refund rounding changes with no assertion on the rounded amount",
		},
		{
			artifactKind: "scm.pull_request",
			artifactId: 4021,
			observedAt: new Date("2026-07-29T08:40:00Z"),
			title: "Retry backoff added without a test for the second attempt",
		},
	],
	occurrenceCount: 3,
	preparedAt: new Date("2026-08-13T06:00:00Z"),
	readAt: new Date("2026-08-13T07:30:00Z"),
};

const changesArriveTooLarge: ReflectionFeedback = {
	id: "2b8c5d7f-2c7d-4d1a-8d3f-1b2c3d4e5f60",
	headline: "Changes are growing past the point where one sitting can review them",
	body: [
		"Two of your recent pull requests opened at more than six hundred changed lines, and both waited",
		"over two days for a first comment. The one you split in half a week later was reviewed the same",
		"afternoon.",
		"",
		"The size looks like it is arriving by accumulation rather than by decision — a rename or a tidy-up",
		"rides along because it was in the way of the change you meant to make.",
		"",
		"**Try next:** when you catch yourself changing something only so the real change fits, open that",
		"part on its own first. It tends to merge while you are still writing the feature.",
	].join("\n"),
	practiceSlug: "keeps-the-change-reviewable",
	practiceName: "Keep a change small enough to review",
	areaSlug: "change-design",
	areaName: "Change design",
	whyItMatters:
		"A reviewer reads a large change more slowly and less carefully than two small ones, so the review that matters most is the one least likely to happen.",
	whatGoodLooksLike:
		"Refactoring travels separately from behaviour, and a change is scoped so a reader can hold all of it in mind at once.",
	evidence: [
		{
			artifactKind: "scm.pull_request",
			artifactId: 4256,
			observedAt: new Date("2026-08-10T11:20:00Z"),
			title: "Rename and behaviour change land together across 41 files",
		},
		{
			artifactKind: "scm.pull_request",
			artifactId: 4102,
			observedAt: new Date("2026-08-01T16:45:00Z"),
			// No title: the review recorded the occurrence without one, so the row is named by its kind.
			title: undefined,
		},
	],
	occurrenceCount: 2,
	preparedAt: new Date("2026-08-13T06:00:00Z"),
	readAt: undefined,
};

const descriptionsSayWhatNotWhy: ReflectionFeedback = {
	id: "3c9d6e80-3d8e-4e2b-9e4a-2c3d4e5f6071",
	headline: "Descriptions say what changed, not why it changed",
	body: [
		"Three of your merge requests this month opened with a description that restated the diff — the",
		"files touched, the methods renamed — and left out the reason the work was needed. On two of them",
		"the first review comment was a question about the reason.",
		"",
		"The reader who needs the description most is the one who was not in the conversation where the",
		"work was decided, and by the time they ask, the answer costs a round trip.",
		"",
		"**Try next:** before you open the next one, write one sentence that would still make sense to",
		"someone reading it in six months, starting with the problem rather than the change.",
	].join("\n"),
	practiceSlug: "explains-why-the-change-exists",
	practiceName: "Explain why the change exists",
	areaSlug: "collaboration",
	areaName: "Working with others",
	whyItMatters:
		"A description is the only part of a change that survives into the future readable, and the reason for a change is the part nobody can reconstruct from the code.",
	whatGoodLooksLike:
		"The description opens with the problem, says what was decided and what was ruled out, and leaves the file-by-file account to the diff.",
	evidence: [
		{
			artifactKind: "scm.pull_request",
			artifactId: 4290,
			observedAt: new Date("2026-08-11T13:05:00Z"),
			title: "Description lists the renamed methods and not the reason for renaming them",
		},
		{
			artifactKind: "scm.issue",
			artifactId: 3980,
			observedAt: new Date("2026-08-04T09:55:00Z"),
			title: "Issue restates the title in the body",
		},
	],
	occurrenceCount: 2,
	preparedAt: new Date("2026-08-12T06:00:00Z"),
	readAt: new Date("2026-08-12T18:10:00Z"),
};

/** A page with several patterns on it, newest first, as the endpoint orders them. */
export const reflectionFeedback: ReflectionFeedback[] = [
	testsWithTheChange,
	changesArriveTooLarge,
	descriptionsSayWhatNotWhy,
];

export const oneReflectionFeedback: ReflectionFeedback[] = [testsWithTheChange];

export const feedbackWithLearnerFraming = testsWithTheChange;
export const feedbackWithoutAnObservationTitle = changesArriveTooLarge;

/** A practice carrying no area and no learner framing: both clauses have to disappear cleanly. */
export const feedbackWithoutFraming: ReflectionFeedback = {
	...testsWithTheChange,
	id: "4d0e7f91-4e9f-4f3c-af5b-3d4e5f607182",
	areaSlug: undefined,
	areaName: undefined,
	whyItMatters: undefined,
	whatGoodLooksLike: undefined,
};

/**
 * Markdown from a model, exercised for the things the renderer refuses: a link that is not `http`,
 * an image, and a fenced block wider than the container.
 */
export const feedbackWithAwkwardMarkdown: ReflectionFeedback = {
	...testsWithTheChange,
	id: "5e1f8092-5f00-4a4d-b06c-4e5f60718293",
	headline: "Assertions are checking that the code ran, not that it was right",
	body: [
		"### Where this shows up",
		"",
		"Across four recent changes the new tests assert that a call returned without throwing, and never",
		"assert what it returned. A test like that passes on code that computes the wrong number.",
		"",
		"```java",
		"assertDoesNotThrow(() -> invoiceTotals.total(customerWhoIsTaxExemptAndAlsoHasAnExpiredDiscountCode));",
		"```",
		"",
		"See [the practice](javascript:alert(1)) and [the testing guide](https://example.com/testing).",
		"",
		"![a screenshot](https://example.com/not-loaded.png)",
		"",
		"**Try next:** write the assertion first and make it fail on purpose, so you have seen it catch",
		"something before you trust it.",
	].join("\n"),
	preparedAt: new Date("2026-08-14T06:00:00Z"),
	readAt: undefined,
};

/** Composition wrote the facts and lost the words. The evidence still has to be readable. */
export const feedbackWithoutABody: ReflectionFeedback = {
	...testsWithTheChange,
	id: "6f2091a3-6011-4b5e-c17d-5f6071829304",
	body: "",
	headline: "",
};
