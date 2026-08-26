import type { Meta, StoryContext, StoryObj } from "@storybook/react-vite";
import { expect, userEvent } from "storybook/test";
import type { DeliveryPolicyTrace as DeliveryPolicyTraceData } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { DeliveryPolicyTrace } from "./DeliveryPolicyTrace";
import { artifactTrace, deniedDeliveryPolicyEvaluation } from "./story-mock-data";

const allowedEvaluation: DeliveryPolicyTraceData = {
	...deniedDeliveryPolicyEvaluation,
	evaluatedRevision: deniedDeliveryPolicyEvaluation.admittedRevision,
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

const openScopeEvaluation: DeliveryPolicyTraceData = {
	...deniedDeliveryPolicyEvaluation,
	surface: "IN_APP",
	stage: "AUTOMATIC",
	evaluatedRevision: undefined,
	decisiveReason: "OUTSIDE_CURRENT_COVERAGE",
	facts: {
		...deniedDeliveryPolicyEvaluation.facts,
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

const conversationEvaluation: DeliveryPolicyTraceData = {
	...deniedDeliveryPolicyEvaluation,
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

async function openTrace(canvas: StoryContext["canvas"]) {
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
		await expect(canvas.getByText("Denied")).toBeVisible();
		await expect(canvas.getByText("Not reached")).toBeVisible();
	},
};

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

export const BroadCoverage: Story = {
	args: { evaluations: [openScopeEvaluation] },
	play: async ({ canvas }) => {
		await openTrace(canvas);
		const scope = await canvas.findByText(/^Scope:/);
		await expect(scope).toHaveTextContent("Repositories: all monitored");
		await expect(scope).toHaveTextContent("People: all eligible");
		await expect(scope).toHaveTextContent("Subject: author is not a workspace member");
		await expect(scope.textContent).not.toMatch(/_/);
		await expect(canvas.getByText(/^In-app feedback · Automatic authorization/)).toBeVisible();
	},
};

export const NoCoverageRecorded: Story = {
	args: { evaluations: [conversationEvaluation] },
	play: async ({ canvas }) => {
		await openTrace(canvas);
		await expect(await canvas.findByText(/^Scope:/)).toHaveTextContent(
			"Scope: no repository · Repositories: not applicable · People: not applicable · Subject: not applicable",
		);
		await expect(canvas.getByText(/^Conversation feedback · Composition/)).toBeVisible();
	},
};

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
