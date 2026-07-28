import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { delay, HttpResponse, http } from "msw";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";
import { AddCollectionDialog } from "./AddCollectionDialog";

/**
 * Searchable multi-select picker for the Outline collections the API token can see. The candidates
 * query is a live proxy to Outline (and the connectivity probe), so every story brings its own MSW
 * handler and a fresh QueryClient.
 */
const meta = {
	component: AddCollectionDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<QueryClientProvider
				client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
			>
				<Story />
			</QueryClientProvider>
		),
	],
	args: {
		workspaceSlug: "demo-workspace",
		open: true,
		onOpenChange: fn(),
		onRegister: fn(),
	},
} satisfies Meta<typeof AddCollectionDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

const candidates = [
	{
		collectionId: "col-engineering",
		name: "Engineering",
		urlId: "engineering-4nZ3x",
		color: "#4E5C6E",
		alreadyMirrored: true,
	},
	{
		collectionId: "col-product",
		name: "Product",
		urlId: "product-2mR8v",
		icon: "🧭",
		alreadyMirrored: false,
	},
	{
		collectionId: "col-design",
		name: "Design System",
		urlId: "design-5tK7q",
		alreadyMirrored: false,
	},
	{
		collectionId: "col-research",
		name: "Research Notes",
		urlId: "research-8pL4m",
		alreadyMirrored: false,
	},
];

type JsonBody = Record<string, unknown> | Record<string, unknown>[];

const candidatesHandler = (body: JsonBody, init?: { status?: number; delayMs?: number }) =>
	http.get("*/workspaces/:workspaceSlug/outline/collections/candidates", async () => {
		if (init?.delayMs) await delay(init.delayMs);
		return HttpResponse.json(body, { status: init?.status ?? 200 });
	});

/** The probe is still in flight — skeleton rows, no premature "nothing here". */
export const Loading: Story = {
	parameters: { msw: { handlers: [candidatesHandler(candidates, { delayMs: 100_000 })] } },
};

/** Outline is unreachable — the 502 ProblemDetail lands inline with a Retry, not an empty list. */
export const ProbeFailed: Story = {
	parameters: {
		msw: {
			handlers: [
				candidatesHandler(
					{
						type: "about:blank",
						title: "Bad Gateway",
						status: 502,
						detail: "Outline did not respond to collections.list.",
					},
					{ status: 502 },
				),
			],
		},
	},
	play: async () => {
		const dialog = await screen.findByRole("dialog");
		await expect(await within(dialog).findByText(/outline did not respond/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^retry$/i })).toBeInTheDocument();
	},
};

/** The token sees nothing — the empty state says how to fix it in Outline (grant the bot access). */
export const NoVisibleCollections: Story = {
	parameters: { msw: { handlers: [candidatesHandler([])] } },
	play: async () => {
		const dialog = await screen.findByRole("dialog");
		await expect(
			await within(dialog).findByText(/this token cannot see any collections/i),
		).toBeInTheDocument();
		await expect(within(dialog).getByText(/add the bot user/i)).toBeInTheDocument();
	},
};

/** Search narrows the list; already-mirrored entries stay visible, checked and disabled. */
export const PopulatedSearchable: Story = {
	parameters: { msw: { handlers: [candidatesHandler(candidates)] } },
	play: async () => {
		const dialog = await screen.findByRole("dialog");

		const mirrored = await within(dialog).findByRole("checkbox", { name: /engineering/i });
		await expect(mirrored).toBeChecked();
		await expect(within(dialog).getByRole("option", { name: /engineering/i })).toHaveAttribute(
			"data-disabled",
		);
		await expect(within(dialog).getByText(/already mirrored/i)).toBeInTheDocument();

		await userEvent.type(within(dialog).getByRole("combobox"), "design");
		await expect(within(dialog).getByText("Design System")).toBeInTheDocument();
		await expect(within(dialog).queryByText("Research Notes")).not.toBeInTheDocument();

		// The option owns the toggle — the checkbox is a read-only mirror of its selected state.
		await userEvent.click(within(dialog).getByRole("option", { name: /design system/i }));
		await expect(within(dialog).getByRole("button", { name: /add 1 collection/i })).toBeEnabled();
	},
};

/**
 * Multi-select: picking a second collection adds to the selection rather than replacing it, and the
 * listbox advertises that with `aria-multiselectable`. Selection survives the search that hid the row.
 */
export const MultiSelect: Story = {
	parameters: { msw: { handlers: [candidatesHandler(candidates)] } },
	play: async () => {
		const dialog = await screen.findByRole("dialog");
		await expect(await within(dialog).findByRole("listbox")).toHaveAttribute(
			"aria-multiselectable",
			"true",
		);

		await userEvent.click(await within(dialog).findByRole("option", { name: /product/i }));
		await userEvent.click(within(dialog).getByRole("option", { name: /design system/i }));

		await expect(within(dialog).getByRole("option", { name: /product/i })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(within(dialog).getByRole("checkbox", { name: /product/i })).toBeChecked();
		await expect(within(dialog).getByRole("button", { name: /add 2 collections/i })).toBeEnabled();

		// Filtering the selected row out of view does not deselect it.
		await userEvent.type(within(dialog).getByRole("combobox"), "research");
		await expect(within(dialog).getByRole("button", { name: /add 2 collections/i })).toBeEnabled();

		// Clicking a selected option again removes it from the selection.
		await userEvent.clear(within(dialog).getByRole("combobox"));
		await userEvent.click(await within(dialog).findByRole("option", { name: /product/i }));
		await expect(within(dialog).getByRole("button", { name: /add 1 collection/i })).toBeEnabled();
	},
};

/**
 * The keyboard contract for the in-dialog list: arrows move a single highlight and Enter toggles the
 * highlighted option without submitting the form.
 *
 * The dialog cannot autofocus the search field — the candidates are still in flight when it opens,
 * so the field does not exist yet at focus time — hence the play clicks into it as a reader would.
 */
export const KeyboardNavigation: Story = {
	parameters: { msw: { handlers: [candidatesHandler(candidates)] } },
	play: async ({ args }) => {
		const dialog = await screen.findByRole("dialog");
		const search = await within(dialog).findByRole("combobox");
		await userEvent.click(search);
		await waitFor(() => expect(search).toHaveFocus());

		// Exactly one option carries the highlight, and the search field points at it.
		await userEvent.keyboard("{ArrowDown}");
		const engineering = within(dialog).getByRole("option", { name: /engineering/i });
		await waitFor(() => expect(engineering).toHaveAttribute("data-highlighted"));
		await expect(dialog.querySelectorAll("[data-highlighted]")).toHaveLength(1);
		await expect(search).toHaveAttribute("aria-activedescendant", engineering.id);

		// ArrowDown advances, ArrowUp returns.
		const product = within(dialog).getByRole("option", { name: /product/i });
		await userEvent.keyboard("{ArrowDown}");
		await waitFor(() => expect(product).toHaveAttribute("data-highlighted"));
		await expect(engineering).not.toHaveAttribute("data-highlighted");

		await userEvent.keyboard("{ArrowUp}");
		await waitFor(() => expect(engineering).toHaveAttribute("data-highlighted"));

		// Enter on the already-mirrored row is refused rather than silently toggling.
		await userEvent.keyboard("{Enter}");
		await expect(within(dialog).getByRole("button", { name: /^add collections$/i })).toBeDisabled();

		// Enter toggles a selectable option, and never submits the form from inside the list.
		await userEvent.keyboard("{ArrowDown}{Enter}");
		await waitFor(() => expect(product).toHaveAttribute("aria-selected", "true"));
		await expect(within(dialog).getByRole("checkbox", { name: /product/i })).toBeChecked();
		await expect(args.onRegister).not.toHaveBeenCalled();

		await userEvent.keyboard("{Enter}");
		await waitFor(() => expect(product).toHaveAttribute("aria-selected", "false"));
	},
};

/** A search that matches nothing keeps the picker and says so, rather than blanking the dialog. */
export const EmptySearchResult: Story = {
	parameters: { msw: { handlers: [candidatesHandler(candidates)] } },
	play: async () => {
		const dialog = await screen.findByRole("dialog");
		await userEvent.type(await within(dialog).findByRole("combobox"), "nothing-matches-this");
		// waitFor, not a one-shot toBeVisible on findByText: the empty node mounts (findable) a frame
		// before the list settles, so a bare assertion races the render. The end state is genuinely
		// visible — this only awaits it.
		await waitFor(() =>
			expect(within(dialog).getByText(/no collections match your search/i)).toBeVisible(),
		);
		await expect(within(dialog).queryByRole("option")).not.toBeInTheDocument();
	},
};

/** Nothing left to add — an empty state instead of a picker with no pickable rows. */
export const AllAlreadyMirrored: Story = {
	parameters: {
		msw: {
			handlers: [candidatesHandler(candidates.map((c) => ({ ...c, alreadyMirrored: true })))],
		},
	},
	play: async () => {
		const dialog = await screen.findByRole("dialog");
		await expect(
			await within(dialog).findByText(/every visible collection is already mirrored/i),
		).toBeInTheDocument();
		await expect(within(dialog).queryByRole("checkbox")).not.toBeInTheDocument();
	},
};

/** Registration is sequential — the run reports its progress in a polite live region. */
export const RegisteringSequentially: Story = {
	parameters: { msw: { handlers: [candidatesHandler(candidates)] } },
	args: {
		onRegister: fn(async () => {
			await new Promise((resolve) => setTimeout(resolve, 400));
		}),
	},
	play: async () => {
		const dialog = await screen.findByRole("dialog");
		await userEvent.click(await within(dialog).findByRole("option", { name: /product/i }));
		await userEvent.click(within(dialog).getByRole("option", { name: /design system/i }));
		await userEvent.click(within(dialog).getByRole("button", { name: /add 2 collections/i }));

		await expect(await within(dialog).findByText(/adding 1 of 2…/i)).toBeInTheDocument();
		await expect(await within(dialog).findByText(/adding 2 of 2…/i)).toBeInTheDocument();
	},
};
