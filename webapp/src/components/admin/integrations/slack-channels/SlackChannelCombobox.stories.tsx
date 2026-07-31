import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";
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
} satisfies Meta<typeof SlackChannelCombobox>;

export default meta;
type Story = StoryObj<typeof meta>;

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

export const Searching: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		const search = await screen.findByPlaceholderText(/search channels/i);

		await userEvent.type(search, "team");
		await expect(await screen.findByRole("option", { name: /#team-standup/i })).toBeInTheDocument();
		await expect(screen.getByRole("option", { name: /#team-listed/i })).toBeInTheDocument();
		await expect(screen.getByRole("option", { name: /#team-archive/i })).toBeInTheDocument();
		await expect(screen.queryByRole("option", { name: /#general/i })).not.toBeInTheDocument();

		await userEvent.clear(search);
		await userEvent.type(search, "C05GENERAL5");
		await expect(await screen.findByRole("option", { name: /#general/i })).toBeInTheDocument();
		await expect(screen.queryByRole("option", { name: /#team-standup/i })).not.toBeInTheDocument();
	},
};

export const KeyboardNavigation: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("combobox");
		await userEvent.click(trigger);

		const search = await screen.findByPlaceholderText(/search channels/i);
		await waitFor(() => expect(search).toHaveFocus());

		await userEvent.keyboard("{ArrowDown}");
		const first = await screen.findByRole("option", { name: /#general/i });
		await waitFor(() => expect(search).toHaveAttribute("aria-activedescendant", first.id));

		await userEvent.keyboard("{ArrowDown}");
		const second = screen.getByRole("option", { name: /#team-standup/i });
		await waitFor(() => expect(search).toHaveAttribute("aria-activedescendant", second.id));

		await userEvent.keyboard("{ArrowUp}");
		await waitFor(() => expect(search).toHaveAttribute("aria-activedescendant", first.id));

		await userEvent.keyboard("{Enter}");
		await expect(args.onSelect).toHaveBeenCalledWith(
			expect.objectContaining({ slackChannelId: "C05GENERAL5" }),
		);
		await expect(trigger).toHaveAttribute("aria-expanded", "false");
	},
};

export const AccessibleStructure: Story = {
	args: { selectedChannelId: "C05GENERAL5" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("combobox");
		await expect(trigger).toHaveAttribute("aria-expanded", "false");

		await userEvent.click(trigger);
		await expect(await screen.findByRole("listbox")).toBeInTheDocument();
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
		await expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
		await expect(screen.getByRole("option", { name: /#general/i })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getByRole("option", { name: /#team-standup/i })).toHaveAttribute(
			"aria-selected",
			"false",
		);
		await expect(screen.getByPlaceholderText(/search channels/i)).toHaveAccessibleName(
			"Search Slack channels",
		);
	},
};

export const Selected: Story = {
	args: { selectedChannelId: "C05GENERAL5" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("combobox")).toHaveTextContent("#general");
	},
};

export const PastedIdNoName: Story = {
	args: { selectedChannelId: "C0974LJBPBK" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("combobox")).toHaveTextContent("C0974LJBPBK");
	},
};

export const WithDisabledReasons: Story = {
	args: {
		getDisabledReason: (candidate) => (candidate.archived ? "Archived" : undefined),
		renderBadges: (candidate) =>
			candidate.consentState === "ACTIVE" ? <Badge variant="success">Monitoring</Badge> : null,
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		const search = await screen.findByPlaceholderText(/search channels/i);
		await waitFor(() => expect(search).toHaveFocus());

		const archived = await screen.findByRole("option", { name: /#team-archive/i });
		await expect(archived).toHaveTextContent("Archived");
		await expect(archived).toHaveAttribute("data-disabled");
		await expect(getComputedStyle(archived).pointerEvents).toBe("none");

		await userEvent.keyboard("{ArrowUp}");
		await waitFor(() => expect(archived).toHaveAttribute("data-highlighted"));
		await userEvent.keyboard("{Enter}");
		await expect(args.onSelect).not.toHaveBeenCalled();
		await expect(archived).toHaveAttribute("aria-selected", "false");
	},
};

export const PrivateChannel: Story = {
	args: { selectedChannelId: "C06STANDUP6" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await expect(await screen.findByRole("img", { name: /private/i })).toBeInTheDocument();
	},
};

export const Empty: Story = {
	args: { candidates: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await expect(await screen.findByText(/no channels found/i)).toBeInTheDocument();
	},
};

export const EmptySearchResult: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await userEvent.type(
			await screen.findByPlaceholderText(/search channels/i),
			"nothing-matches-this",
		);
		await expect(await screen.findByText(/no channels found/i)).toBeInTheDocument();
		await expect(screen.queryByRole("option")).not.toBeInTheDocument();
	},
};

export const Disabled: Story = { args: { disabled: true } };

export const Invalid: Story = { args: { invalid: true } };
