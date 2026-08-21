import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent } from "storybook/test";
import type { DeliveryPolicyTrace as DeliveryPolicyTraceData } from "@/api/types.gen";
import { DeliveryPolicyTrace } from "./DeliveryPolicyTrace";
import { artifactTrace } from "./story-mock-data";

const [deniedEvaluation] = artifactTrace.deliveryPolicy;

const allowedEvaluation: DeliveryPolicyTraceData = {
	...deniedEvaluation,
	evaluatedRevision: deniedEvaluation.admittedRevision,
	allowed: true,
	decisiveReason: undefined,
	facts: { ...deniedEvaluation.facts, recipientConsent: true },
	checks: deniedEvaluation.checks.map((check) => ({
		...check,
		status: check.status === "NOT_APPLICABLE" ? check.status : "PASSED",
	})),
};

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
		await userEvent.click(canvas.getByText("Technical delivery policy trace"));
		await expect(canvas.getByText("Stopped because: Recipient opted out")).toBeVisible();
		await expect(canvas.getByText("Denied")).toBeVisible();
	},
};

/** Nothing stopped it, so there is no decisive reason to name and no check left unreached. */
export const Allowed: Story = {
	args: { evaluations: [allowedEvaluation] },
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByText("Technical delivery policy trace"));
		await expect(canvas.queryByText(/^Stopped because:/)).not.toBeInTheDocument();
		await expect(canvas.queryByText("Not reached")).not.toBeInTheDocument();
	},
};
