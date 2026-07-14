import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SlackChannelCandidate } from "@/api/types.gen";
import { AddChannelDialog } from "./AddChannelDialog";

const candidates: SlackChannelCandidate[] = [
	{
		slackChannelId: "C05GENERAL5",
		channelName: "general",
		privateChannel: false,
		member: true,
		archived: false,
	},
	{
		slackChannelId: "C06STANDUP6",
		channelName: "team-standup",
		privateChannel: true,
		member: true,
		archived: false,
	},
	{
		slackChannelId: "C07LISTED07",
		channelName: "team-listed",
		privateChannel: false,
		member: true,
		archived: false,
		consentState: "ACTIVE",
	},
	{
		slackChannelId: "C08OLDIES08",
		channelName: "team-archive",
		privateChannel: false,
		member: true,
		archived: true,
	},
];

/**
 * Allow-listing a channel. One primary control — a combobox over the channels Slack reports —
 * with a paste escape hatch for channels Slack did not list. The stable id is what gets stored;
 * it is never something the admin has to read or retype.
 *
 * The dialog is portalled, so the plays query the document rather than the story canvas.
 */
const meta = {
	component: AddChannelDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		onSubmit: fn(),
		candidates,
	},
} satisfies Meta<typeof AddChannelDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Slack listed channels — pick one; disabled options keep a visible reason. */
export const WithCandidates: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.click(dialog.getByRole("combobox", { name: /^channel$/i }));

		await expect(await screen.findByRole("option", { name: /#team-archive/i })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(await screen.findByRole("option", { name: /#team-listed/i })).toHaveAttribute(
			"aria-disabled",
			"true",
		);

		await userEvent.click(screen.getByRole("option", { name: /#general/i }));
		await userEvent.click(dialog.getByRole("button", { name: /^add channel$/i }));

		await expect(args.onSubmit).toHaveBeenCalledWith({
			slackChannelId: "C05GENERAL5",
			channelName: "general",
		});
	},
};

/** Nothing chosen — submit stays enabled and says what is missing instead of going dead. */
export const NothingChosen: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		const submit = dialog.getByRole("button", { name: /^add channel$/i });
		await expect(submit).toBeEnabled();

		await userEvent.click(submit);

		await expect(args.onSubmit).not.toHaveBeenCalled();
		await expect(dialog.getByText(/choose a channel from the list/i)).toBeInTheDocument();
	},
};

/** Slack listed nothing (no channels yet, or the bot was never invited) — paste is the path. */
export const NoCandidates: Story = {
	args: { candidates: [] },
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.type(
			dialog.getByLabelText(/paste a channel link or id/i),
			"https://acme.slack.com/archives/C0974LJBPBK",
		);
		await userEvent.click(dialog.getByRole("button", { name: /^add channel$/i }));

		await expect(args.onSubmit).toHaveBeenCalledWith({
			slackChannelId: "C0974LJBPBK",
			channelName: undefined,
		});
	},
};

/** A pasted value that is not a Slack channel reference is rejected in place, before submit. */
export const InvalidPaste: Story = {
	args: { candidates: [] },
	play: async () => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.type(dialog.getByLabelText(/paste a channel link or id/i), "not-a-channel");
		await expect(dialog.getByText(/paste a slack channel url, mention, or/i)).toBeInTheDocument();
	},
};

/** A rejected registration keeps the dialog open so the admin can fix and retry. */
export const SubmitRejected: Story = {
	args: {
		candidates: [],
		onSubmit: fn(async () => {
			throw new Error("slack rejected the channel");
		}),
	},
	play: async () => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.type(dialog.getByLabelText(/paste a channel link or id/i), "C0974LJBPBK");
		await userEvent.click(dialog.getByRole("button", { name: /^add channel$/i }));

		await expect(await screen.findByRole("dialog")).toBeInTheDocument();
	},
};
