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

/**
 * The single control for choosing a Slack channel: a Base UI Combobox whose trigger shows the human
 * `#channel-name` while the stored value — the stable Slack id — is never surfaced as editable text.
 * Search, the roving highlight and the marked selection come from the primitive, so the popup
 * keyboards and paints like every other Base UI list in the app. Disabled options keep a visible
 * reason instead of vanishing, private channels carry a lock, and an id with no name is labelled as
 * such rather than passed off as a name. Shared by the add-channel dialog and the weekly-digest field.
 *
 * The popup is portalled, so the plays query the document rather than the story canvas.
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

/**
 * Typeahead narrows the list to the matches, and searching by the stable id works too — a channel
 * reference pasted from a Slack link finds its row without the reader knowing the name.
 */
export const Searching: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		const search = await screen.findByPlaceholderText(/search channels/i);

		await userEvent.type(search, "team");
		await expect(await screen.findAllByRole("option")).toHaveLength(3);
		await expect(screen.queryByRole("option", { name: /#general/i })).not.toBeInTheDocument();

		// The id is searchable, not just the name.
		await userEvent.clear(search);
		await userEvent.type(search, "C05GENERAL5");
		await expect(await screen.findAllByRole("option")).toHaveLength(1);
		await expect(screen.getByRole("option", { name: /#general/i })).toBeInTheDocument();
	},
};

/**
 * The keyboard contract, exercised for real: arrows move the highlight, the highlight is tracked
 * through `aria-activedescendant`, Enter selects, and Escape closes and returns focus to the trigger.
 */
export const KeyboardNavigation: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("combobox");
		await userEvent.click(trigger);

		// Focus lands in the popup's search field, so typing filters without an extra Tab.
		const search = await screen.findByPlaceholderText(/search channels/i);
		await waitFor(() => expect(search).toHaveFocus());

		// Exactly one option is highlighted at a time, or the moving highlight is invisible.
		await userEvent.keyboard("{ArrowDown}");
		const first = await screen.findByRole("option", { name: /#general/i });
		await waitFor(() => expect(first).toHaveAttribute("data-highlighted"));
		await expect(search).toHaveAttribute("aria-activedescendant", first.id);
		await expect(document.querySelectorAll("[data-highlighted]")).toHaveLength(1);

		// ArrowDown advances the highlight, ArrowUp brings it back.
		await userEvent.keyboard("{ArrowDown}");
		const second = screen.getByRole("option", { name: /#team-standup/i });
		await waitFor(() => expect(second).toHaveAttribute("data-highlighted"));
		await expect(first).not.toHaveAttribute("data-highlighted");
		await expect(search).toHaveAttribute("aria-activedescendant", second.id);

		await userEvent.keyboard("{ArrowUp}");
		await waitFor(() => expect(first).toHaveAttribute("data-highlighted"));
		await expect(second).not.toHaveAttribute("data-highlighted");

		// Enter selects the highlighted option and closes the popup. The options outlive the close
		// by one exit animation, so the assertion waits for the popup to actually go.
		await userEvent.keyboard("{Enter}");
		await expect(args.onSelect).toHaveBeenCalledWith(
			expect.objectContaining({ slackChannelId: "C05GENERAL5" }),
		);
		await waitFor(() => expect(screen.queryAllByRole("option")).toHaveLength(0));

		// Escape closes and hands focus back to the trigger rather than dropping it on the body.
		await userEvent.click(trigger);
		await expect(await screen.findByPlaceholderText(/search channels/i)).toBeInTheDocument();
		await userEvent.keyboard("{Escape}");
		await waitFor(() => expect(screen.queryAllByRole("option")).toHaveLength(0));
		await waitFor(() => expect(trigger).toHaveFocus());
	},
};

/** The listbox/option roles and the selected marking are the primitive's, not hand-rolled. */
export const AccessibleStructure: Story = {
	args: { selectedChannelId: "C05GENERAL5" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("combobox");
		await expect(trigger).toHaveAttribute("aria-expanded", "false");

		await userEvent.click(trigger);
		await expect(await screen.findByRole("listbox")).toBeInTheDocument();
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
		// The trigger owns the combobox role while the input sits inside the popup, so it also
		// advertises that pressing it opens a dialog rather than a bare listbox.
		await expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
		await expect(screen.getByRole("option", { name: /#general/i })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getByRole("option", { name: /#team-standup/i })).toHaveAttribute(
			"aria-selected",
			"false",
		);
		// The search field carries the caller's accessible name.
		await expect(screen.getByPlaceholderText(/search channels/i)).toHaveAccessibleName(
			"Search Slack channels",
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
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		const search = await screen.findByPlaceholderText(/search channels/i);
		await waitFor(() => expect(search).toHaveFocus());

		// The reason stays on the row instead of the row disappearing.
		const archived = await screen.findByRole("option", { name: /#team-archive/i });
		await expect(archived).toHaveTextContent("Archived");
		await expect(archived).toHaveAttribute("data-disabled");
		// Disabled options are inert to the pointer, which is what stops them being clicked.
		await expect(getComputedStyle(archived).pointerEvents).toBe("none");

		// A disabled option stays reachable by keyboard on purpose — a reader who cannot see the
		// list still hears the row and its reason — but it refuses to be chosen.
		await userEvent.keyboard("{ArrowUp}");
		await waitFor(() => expect(archived).toHaveAttribute("data-highlighted"));
		await userEvent.keyboard("{Enter}");
		await expect(args.onSelect).not.toHaveBeenCalled();
		await expect(archived).toHaveAttribute("aria-selected", "false");
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

/** No candidates at all — the list shows an honest empty message instead of a blank popup. */
export const Empty: Story = {
	args: { candidates: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		await expect(await screen.findByText(/no channels found/i)).toBeInTheDocument();
	},
};

/** A search that matches nothing falls back to the same empty message. */
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

/** Disabled — the trigger is inert (e.g. while the candidate list is still loading). */
export const Disabled: Story = { args: { disabled: true } };

/** Invalid — the trigger is marked aria-invalid to pair with a field-level error. */
export const Invalid: Story = { args: { invalid: true } };
