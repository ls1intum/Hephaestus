import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SlackChannelCandidate } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { SlackChannelCombobox } from "./SlackChannelCombobox";

const candidates: SlackChannelCandidate[] = [
	{ slackChannelId: "C05GENERAL5", channelName: "general", privateChannel: false, member: true },
	{
		slackChannelId: "C06STANDUP6",
		channelName: "team-standup",
		privateChannel: true,
		member: true,
	},
	{
		slackChannelId: "C07LISTED07",
		channelName: "team-listed",
		privateChannel: false,
		member: true,
		consentState: "ACTIVE",
	},
	{
		slackChannelId: "C08OLDIES08",
		channelName: "team-archive",
		privateChannel: false,
		member: false,
		archived: true,
	},
];

/**
 * The single control for choosing a Slack channel: a Popover + Command combobox whose trigger shows
 * the human `#channel-name` while the stored value — the stable Slack id — is never surfaced as
 * editable text. Search and roving keyboard focus come from cmdk; disabled options keep a visible
 * reason instead of vanishing, private channels carry a lock, and an id with no name is labelled as
 * such rather than passed off as a name. Shared by the add-channel dialog and the weekly-digest field.
 *
 * The popover list is portalled, so the plays query the document rather than the story canvas.
 */
const meta = {
	component: SlackChannelCombobox,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="w-80">
				<Story />
			</div>
		),
	],
	args: {
		candidates,
		onSelect: fn(),
	},
	argTypes: {
		disabled: { control: "boolean" },
		invalid: { control: "boolean", description: "Marks the trigger aria-invalid." },
		placeholder: { control: "text" },
	},
} satisfies Meta<typeof SlackChannelCombobox>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing chosen yet — the trigger shows the placeholder; open, search and pick a channel. */
export const Default: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));

		await userEvent.type(await screen.findByPlaceholderText(/search channels/i), "general");
		await userEvent.click(await screen.findByRole("option", { name: /#general/i }));

		await expect(args.onSelect).toHaveBeenCalledWith(
			expect.objectContaining({ slackChannelId: "C05GENERAL5" }),
		);
	},
};

/** A selected id that Slack listed — the trigger resolves it to `#channel-name`. */
export const Selected: Story = {
	args: { selectedChannelId: "C05GENERAL5" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("combobox")).toHaveTextContent("#general");
	},
};

/** A pasted id Slack never listed and that carried no name — labelled as an id, not faked as a name. */
export const PastedIdNoName: Story = {
	args: { selectedChannelId: "C0974LJBPBK" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("combobox")).toHaveTextContent("C0974LJBPBK");
	},
};

/** Options can be disabled with a stated reason (kept visible) rather than removed from the list. */
export const WithDisabledReasons: Story = {
	args: {
		getDisabledReason: (candidate) => (candidate.archived ? "Archived" : undefined),
		renderBadges: (candidate) =>
			candidate.consentState === "ACTIVE" ? <Badge variant="success">Monitoring</Badge> : null,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await expect(await screen.findByRole("option", { name: /#team-archive/i })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

/** Private channels carry a lock affordance so their scope is legible. */
export const PrivateChannel: Story = {
	args: { selectedChannelId: "C06STANDUP6" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await expect(await screen.findByRole("img", { name: /private/i })).toBeInTheDocument();
	},
};

/** No candidates — the list shows an honest empty message instead of a blank popover. */
export const Empty: Story = {
	args: { candidates: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await expect(await screen.findByText(/no channels found/i)).toBeInTheDocument();
	},
};

/** Disabled — the trigger is inert (e.g. while the candidate list is still loading). */
export const Disabled: Story = { args: { disabled: true } };

/** Invalid — the trigger is marked aria-invalid to pair with a field-level error. */
export const Invalid: Story = { args: { invalid: true } };
