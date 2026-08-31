import type { Meta, StoryObj } from "@storybook/react-vite";
import { EvidenceFileBlock } from "./EvidenceFileBlock";

const meta = {
	component: EvidenceFileBlock,
	tags: ["autodocs"],
	title: "Profile/EvidenceFileBlock",
	parameters: {
		docs: {
			description: {
				component:
					"One quoted citation behind an observation: its source as header, the quote beneath. A code " +
					"source is located by line and keeps a pinned gutter; an object source is not, because its " +
					"numbers are offsets into a serialised file rather than places a reader could open.",
			},
		},
	},
	decorators: [
		(Story) => (
			<div className="max-w-lg p-4">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof EvidenceFileBlock>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		location: {
			path: "server/src/main/java/de/tum/cit/aet/hephaestus/practices/review/ReadyAndTraceableHandoff.java",
			startLine: 60,
			endLine: 64,
			sourceKind: "scm.pull-request.diff",
			redacted: false,
			snippet: [
				"  public DeliveryResult handoff(ReviewRequest request) {",
				"    var context = contextBuilder.build(request);",
				"    ReviewResult result = reviewService.evaluate(context);",
				"    return deliveryService.publish(result, request.recipient());",
				"  }",
			].join("\n"),
		},
	},
};

/** A single named line, the shape most observations carry. */
export const SingleLine: Story = {
	args: {
		location: {
			path: "webapp/src/routes/_authenticated/w/$workspaceSlug/user/$username/index.tsx",
			startLine: 118,
			endLine: 118,
			sourceKind: "scm.pull-request.diff",
			redacted: false,
			snippet:
				"  const statusesQuery = useQuery(listPracticeGroupStandingsOptions({ path: { workspaceSlug } }));",
		},
	},
};

/** Collapsed: the second and later files of a observation start closed so the panel stays scannable. */
export const Collapsed: Story = {
	args: {
		defaultOpen: false,
		location: {
			path: "server/src/main/resources/db/changelog/1786939608194_changelog.xml",
			startLine: 12,
			endLine: 14,
			sourceKind: "scm.pull-request.diff",
			redacted: false,
			snippet:
				'<changeSet id="1786939608194-1" author="hephaestus">\n  <addColumn tableName="observation" />\n</changeSet>',
		},
	},
};

/** The quote was withheld and the app cannot know why: it says only that, and names the place. */
export const Redacted: Story = {
	args: {
		location: {
			path: "docs/contributor/practice-catalogue.md",
			startLine: 31,
			endLine: 44,
			sourceKind: "scm.pull-request.diff",
			redacted: true,
		},
	},
};

/**
 * The secret scanner is the one detector allowed to omit a quote, so here the reason *is* knowable
 * and gets said — the text was never stored, and the reader is pointed at the line to read it there.
 */
export const RedactedBySecretScanner: Story = {
	args: {
		location: {
			path: "server/application/src/main/resources/application-local.yml",
			startLine: 12,
			endLine: 12,
			sourceKind: "scm.pull-request.diff",
			side: "NEW",
			redacted: true,
		},
		detector: "secret-diff-scanner",
	},
};

/** No directory to absorb truncation — the file name carries the whole identity. */
export const BareFileName: Story = {
	args: {
		location: {
			path: "CHANGELOG.md",
			startLine: 1,
			endLine: 1,
			sourceKind: "scm.pull-request.diff",
			redacted: false,
			snippet: "## 0.14.0",
		},
	},
};

/** A line far wider than the block: it scrolls, and the gutter stays pinned to the left edge. */
export const LongLines: Story = {
	args: {
		location: {
			path: "server/src/main/java/de/tum/cit/aet/hephaestus/practices/observation/ObservationRepository.java",
			startLine: 1,
			endLine: 2,
			sourceKind: "scm.pull-request.diff",
			redacted: false,
			snippet: [
				'SELECT o.agent_job_id AS "jobId", MAX(o.observed_at) AS "reviewedAt" FROM observation o JOIN practice p ON p.id = o.practice_id JOIN practice_group g ON g.id = p.practice_group_id',
				"WHERE o.about_user_id = :aboutUserId AND p.workspace_id = :workspaceId AND g.slug = :groupSlug AND o.presence <> 'NOT_APPLICABLE'",
			].join("\n"),
		},
	},
};

/**
 * A quote from an object source. Its numbers are offsets into a serialised context file, not lines
 * of anything the reader could open, so no gutter and no range are shown — only the source and the
 * quote. This is the case that used to render a Slack message as though it were code.
 */
export const ObjectSource: Story = {
	args: {
		location: {
			path: "conversation_thread.json",
			startLine: 62,
			endLine: 70,
			sourceKind: "slack.conversation.thread",
			redacted: false,
			snippet: [
				"@marta: are we rolling this out behind the flag, or straight to everyone?",
				"@jon: behind the flag — I want a day of telemetry before we widen it.",
				"@marta: works for me. I'll write the rollback step into the runbook.",
			].join("\n"),
		},
	},
};
