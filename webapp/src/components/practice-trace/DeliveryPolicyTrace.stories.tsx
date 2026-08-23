import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, type within } from "storybook/test";
import type { DeliveryPolicyTrace as DeliveryPolicyTraceData } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { DeliveryPolicyTrace } from "./DeliveryPolicyTrace";
import { artifactTrace } from "./story-mock-data";

const [deniedEvaluation] = artifactTrace.deliveryPolicy;

const allowedEvaluation: DeliveryPolicyTraceData = {
	...deniedEvaluation,
	evaluatedRevision: deniedEvaluation.admittedRevision,
	allowed: true,
	decisiveReason: undefined,
	checks: [
		{ check: "INSTANCE_SILENT_MODE", status: "PASSED" },
		{ check: "WORKSPACE_ENABLED", status: "PASSED" },
		{ check: "ROLLOUT_REVISION", status: "PASSED" },
		{ check: "WORKSPACE_DELIVERY", status: "PASSED" },
		{ check: "CURRENT_COVERAGE", status: "PASSED" },
		{ check: "PRACTICE_AUTHORITY", status: "PASSED" },
		{ check: "RECIPIENT_CONSENT", status: "PASSED" },
		{ check: "ARTIFACT_ELIGIBILITY", status: "PASSED" },
	],
};

/** The open end of every scope axis, and a denial early enough to leave four checks unreached. */
const openScopeEvaluation: DeliveryPolicyTraceData = {
	...deniedEvaluation,
	surface: "IN_APP",
	stage: "AUTOMATIC",
	evaluatedRevision: undefined,
	decisiveReason: "OUTSIDE_CURRENT_COVERAGE",
	facts: {
		...deniedEvaluation.facts,
		repositoryMode: "ALL_MONITORED",
		personMode: "ALL_ELIGIBLE",
		subject: "UNLINKED",
	},
	checks: [
		{ check: "INSTANCE_SILENT_MODE", status: "PASSED" },
		{ check: "WORKSPACE_ENABLED", status: "PASSED" },
		{ check: "ROLLOUT_REVISION", status: "PASSED" },
		{ check: "WORKSPACE_DELIVERY", status: "PASSED" },
		{ check: "CURRENT_COVERAGE", status: "DENIED" },
		{ check: "PRACTICE_AUTHORITY", status: "NOT_REACHED" },
		{ check: "RECIPIENT_CONSENT", status: "NOT_REACHED" },
		{ check: "ARTIFACT_ELIGIBILITY", status: "NOT_REACHED" },
	],
};

/** A conversation carries no repository, so every scope axis is absent rather than open or narrow. */
const conversationEvaluation: DeliveryPolicyTraceData = {
	...deniedEvaluation,
	surface: "CONVERSATION",
	stage: "COMPOSITION",
	evaluatedRevision: undefined,
	decisiveReason: "CONVERSATION_EXPIRED",
	facts: {
		artifactKind: "chat.conversation_thread",
		deliveryStatus: "ACTIVE",
		triggerMode: "AUTO",
	},
	checks: [
		{ check: "INSTANCE_SILENT_MODE", status: "PASSED" },
		{ check: "WORKSPACE_ENABLED", status: "PASSED" },
		{ check: "ROLLOUT_REVISION", status: "PASSED" },
		{ check: "WORKSPACE_DELIVERY", status: "PASSED" },
		{ check: "CURRENT_COVERAGE", status: "NOT_APPLICABLE" },
		{ check: "PRACTICE_AUTHORITY", status: "PASSED" },
		{ check: "RECIPIENT_CONSENT", status: "PASSED" },
		{ check: "ARTIFACT_ELIGIBILITY", status: "DENIED" },
	],
};

type Canvas = ReturnType<typeof within>;

async function openTrace(canvas: Canvas) {
	await userEvent.click(canvas.getByRole("button", { name: "Technical delivery policy trace" }));
}

const meta = {
	title: "Workspace admin/Practices/Trace/Delivery policy",
	component: DeliveryPolicyTrace,
	tags: ["autodocs"],
	args: { evaluations: artifactTrace.deliveryPolicy },
} satisfies Meta<typeof DeliveryPolicyTrace>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Denied: Story = {
	play: async ({ canvas }) => {
		await openTrace(canvas);
		await expect(
			await canvas.findByText(
				"In-context feedback · Final delivery · admitted revision 4 · evaluated revision 5",
			),
		).toBeVisible();
		await expect(
			canvas.getByText("Stopped here. The developer has opted out of AI feedback."),
		).toBeVisible();
		// The two statuses a denial produces, each in its own words. NOT_APPLICABLE is a state of the
		// artifact rather than of the denial, so it belongs to NoScopeRecorded.
		await expect(canvas.getByText("Denied")).toBeVisible();
		await expect(canvas.getByText("Not reached")).toBeVisible();
	},
};

/** Nothing stopped it, so there is no decisive reason to name. */
export const Allowed: Story = {
	args: { evaluations: [allowedEvaluation] },
	play: async ({ canvas }) => {
		await openTrace(canvas);
		await expect(
			await canvas.findByText(
				"In-context feedback · Final delivery · admitted revision 4 · evaluated revision 4",
			),
		).toBeVisible();
		await expect(canvas.queryByText(/^Stopped here\./)).not.toBeInTheDocument();
	},
};

/**
 * The scope axes read as words, never as the constant the wire carries: `ALL_MONITORED` reaching the
 * page as "all_monitored repositories" is the regression this story exists to catch.
 */
export const OpenScope: Story = {
	args: { evaluations: [openScopeEvaluation] },
	play: async ({ canvas }) => {
		await openTrace(canvas);
		const scope = await canvas.findByText(/^Scope:/);
		await expect(scope).toHaveTextContent("Repositories: all monitored");
		await expect(scope).toHaveTextContent("People: all eligible");
		await expect(scope).toHaveTextContent("Subject: author not linked");
		await expect(scope.textContent).not.toMatch(/_/);
		// Surface and stage are the two the other stories do not reach.
		await expect(canvas.getByText(/^In-app feedback · Automatic authorization/)).toBeVisible();
	},
};

/** No repository and no modes recorded: each axis says which one it has nothing to report on. */
export const NoScopeRecorded: Story = {
	args: { evaluations: [conversationEvaluation] },
	play: async ({ canvas }) => {
		await openTrace(canvas);
		await expect(await canvas.findByText(/^Scope:/)).toHaveTextContent(
			"Scope: no repository · Repositories: not applicable · People: not applicable · Subject: not applicable",
		);
		await expect(canvas.getByText(/^Conversation feedback · Composition/)).toBeVisible();
	},
};

/** Every check row is a label pushed away from its badge, which is what overflows first. */
export const Reflow: Story = {
	args: {
		evaluations: [
			{
				...openScopeEvaluation,
				facts: {
					...openScopeEvaluation.facts,
					baseBranch: "release/2026.09-bounded-practice-review-rollout",
				},
			},
		],
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvas }) => {
		await openTrace(canvas);
		await expect(await canvas.findByText("Current review coverage")).toBeVisible();
		await expectNoPageOverflow();
	},
};
