import type { Meta, StoryObj } from "@storybook/react-vite";
import { EvidenceFileBlock } from "./EvidenceFileBlock";

const meta = {
	component: EvidenceFileBlock,
	title: "Profile/EvidenceFileBlock",
	parameters: {
		docs: {
			description: {
				component:
					"One quoted file behind a finding: path as header, lines beneath. The path truncates in the " +
					"middle so the file name survives, and long lines scroll with the line gutter pinned.",
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

/** A single named line, the shape most findings carry. */
export const SingleLine: Story = {
	args: {
		location: {
			path: "webapp/src/routes/_authenticated/w/$workspaceSlug/user/$username/index.tsx",
			startLine: 118,
			endLine: 118,
			redacted: false,
			snippet:
				"  const statusesQuery = useQuery(getPracticeAreaStatusesOptions({ path: { workspaceSlug } }));",
		},
	},
};

/** Collapsed: the second and later files of a finding start closed so the panel stays scannable. */
export const Collapsed: Story = {
	args: {
		defaultOpen: false,
		location: {
			path: "server/src/main/resources/db/changelog/1786939608194_changelog.xml",
			startLine: 12,
			endLine: 14,
			redacted: false,
			snippet:
				'<changeSet id="1786939608194-1" author="hephaestus">\n  <addColumn tableName="observation" />\n</changeSet>',
		},
	},
};

/** The quote was withheld: the place is still named, and the block says the omission was deliberate. */
export const Redacted: Story = {
	args: {
		location: {
			path: "docs/contributor/practice-catalogue.md",
			startLine: 31,
			endLine: 44,
			redacted: true,
		},
	},
};

/** No directory to absorb truncation — the file name carries the whole identity. */
export const BareFileName: Story = {
	args: {
		location: {
			path: "CHANGELOG.md",
			startLine: 1,
			endLine: 1,
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
			redacted: false,
			snippet: [
				'SELECT o.agent_job_id AS "jobId", MAX(o.observed_at) AS "reviewedAt" FROM observation o JOIN practice p ON p.id = o.practice_id JOIN practice_area a ON a.id = p.practice_area_id',
				"WHERE o.about_user_id = :aboutUserId AND p.workspace_id = :workspaceId AND a.slug = :areaSlug AND o.presence <> 'NOT_APPLICABLE'",
			].join("\n"),
		},
	},
};
