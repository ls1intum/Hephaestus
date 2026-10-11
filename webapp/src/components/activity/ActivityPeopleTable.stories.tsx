import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, waitFor, within } from "storybook/test";

import { LARGE_PEOPLE, PEOPLE, peopleOf, readyPeople } from "@/stories/activity-story-data";
import { withProvider, withStandardPage } from "@/stories/decorators";
import { settledPopup } from "@/stories/overlay";
import { expectNoPageOverflow } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { ActivityPeopleTable } from "./ActivityPeopleTable";
import { personLevelLink } from "./people-links";

const meta = {
	component: ActivityPeopleTable,
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		state: readyPeople(peopleOf(PEOPLE)),
		providerType: "GITHUB",
		order: { sort: "contributions", desc: true },
		onOrderChange: fn(),
		repo: [],
		personLink: personLevelLink,
	},
	render: (args) => (
		<Stateful initial={args.order}>
			{(order, setOrder) => (
				<ActivityPeopleTable
					{...args}
					order={order}
					onOrderChange={(next) => {
						args.onOrderChange(next);
						setOrder(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof ActivityPeopleTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Each row's position and name, in the order the table shows them. */
function rows(table: HTMLElement): string[][] {
	return within(table)
		.getAllByRole("row")
		.slice(1)
		.map((row) => [
			within(row).getAllByRole("cell")[0]?.textContent ?? "",
			within(row).getByRole("link").textContent,
		]);
}

/** Most contributions first; Bob and Dana tie, share position 2, and the next is 4. */
export const Default: Story = {
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "People" });
		await expect(rows(table)).toStrictEqual([
			["1", "Ada Lovelace"],
			["2", "Bob Brenner"],
			["2", "Dana Okafor"],
			["4", "Chen Wei"],
			["5", "Élodie Brière"],
		]);
		await expect(within(table).getByRole("columnheader", { name: "Position" })).toBeVisible();
		// Figures read right-aligned, a cell with nothing in it is one dash read as "None", and a
		// first contribution in the range is marked new.
		const contributions = within(table).getAllByRole("cell")[2];
		await expect(contributions && getComputedStyle(contributions).textAlign).toBe("right");
		const chen = within(table).getByRole("row", { name: /Chen Wei/u });
		const chenPullRequests = within(chen).getAllByRole("cell")[3];
		await expect(chenPullRequests?.textContent).toBe("—None");
		await expect(within(chen).queryByText("New")).not.toBeInTheDocument();
		const elodie = within(table).getByRole("row", { name: /Élodie Brière/u });
		await expect(
			within(elodie).getByRole("img", { name: "First contribution in this range" }),
		).toBeVisible();
		await expect(
			within(table).getByRole("columnheader", { name: /^Contributions/u }),
		).toHaveAttribute("aria-sort", "descending");
		const trend = within(table).getByRole("columnheader", { name: "Weekly trend" });
		await expect(within(trend).getByText("Trend")).toBeVisible();
	},
};

/** In a row, every icon is reachable by the pointer and says what it counts. */
export const ChipTooltips: Story = {
	play: async ({ canvas, userEvent }) => {
		const ada = within(canvas.getByRole("table", { name: "People" })).getByRole("row", {
			name: /Ada Lovelace/u,
		});
		for (const part of within(ada).getAllByRole("img")) {
			// What a pointer at the part's centre hits: the part, not the row's stretched link over it.
			const box = part.getBoundingClientRect();
			const hit = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
			await expect(part.contains(hit)).toBe(true);
		}
		const helped = within(ada).getByRole("img", { name: /^Reviewed the work of \d+ people$/u });
		await userEvent.hover(helped);
		await expect(await settledPopup()).toHaveTextContent(/^Reviewed the work of \d+ people$/u);
	},
};

/** A press on a count sorts by it, most first, and says so to a screen reader. */
export const SortByReviews: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /^Reviews/u }));
		await expect(args.onOrderChange).toHaveBeenCalledWith({ sort: "reviews", desc: true });
		await expect(canvas.getByRole("columnheader", { name: /^Reviews/u })).toHaveAttribute(
			"aria-sort",
			"descending",
		);
	},
};

/** In name order no row has a position. */
export const ByName: Story = {
	args: { order: { sort: "name", desc: false } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("columnheader", { name: "Position" })).not.toBeInTheDocument();
	},
};

/** A first name sort runs A to Z even when a search currently leaves no rows. */
export const NameAfterNoMatches: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas, userEvent }) => {
		const search = canvas.getByRole("searchbox", { name: "Search people" });
		await userEvent.type(search, "zz");
		await expect(window.innerWidth).toBe(320);
		const table = canvas.getByRole("table", { name: "People" });
		const container = table.closest<HTMLElement>("[data-slot='table-container']");
		if (!container) {
			throw new Error("The table has no scroll container");
		}
		const title = canvas.getByText("No one matches your search");
		await expect(title).toBeVisible();
		for (const scrollLeft of [0, container.scrollWidth]) {
			container.scrollLeft = scrollLeft;
			const box = title.getBoundingClientRect();
			await expect(box.width).toBeGreaterThan(0);
			await expect(box.left).toBeGreaterThanOrEqual(0);
			await expect(box.right).toBeLessThanOrEqual(window.innerWidth);
		}
		await expect(container.scrollLeft).toBeGreaterThan(0);
		container.scrollLeft = 0;
		await expectNoPageOverflow();
		await userEvent.click(canvas.getByRole("button", { name: "Person" }));
		await expect(canvas.getByRole("columnheader", { name: "Person" })).toHaveAttribute(
			"aria-sort",
			"ascending",
		);
		await userEvent.clear(search);
		await expect(canvas.queryByText("No one matches your search")).not.toBeInTheDocument();
		await expect(
			within(table)
				.getAllByRole("link")
				.map((link) => link.textContent),
		).toStrictEqual(["Ada Lovelace", "Bob Brenner", "Chen Wei", "Dana Okafor", "Élodie Brière"]);
		await userEvent.click(canvas.getByRole("button", { name: "Person" }));
		await expect(canvas.getByRole("columnheader", { name: "Person" })).toHaveAttribute(
			"aria-sort",
			"descending",
		);
		await expect(
			within(table)
				.getAllByRole("link")
				.map((link) => link.textContent),
		).toStrictEqual(["Élodie Brière", "Dana Okafor", "Chen Wei", "Bob Brenner", "Ada Lovelace"]);
	},
};

/** A search hides rows but keeps their positions, and folds case and accents. */
export const Search: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.type(canvas.getByRole("searchbox", { name: "Search people" }), "elodie");
		await expect(rows(canvas.getByRole("table", { name: "People" }))).toStrictEqual([
			["5", "Élodie Brière"],
		]);
		await expect(canvas.getByText("1 of 5 people")).toBeVisible();
	},
};

export const NoMatch: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.type(canvas.getByRole("searchbox", { name: "Search people" }), "zz");
		await expect(canvas.getByText("No one matches your search")).toBeVisible();
	},
};

/** 250 people render 50 at a time; the end's press stays for the keyboard. */
export const Large: Story = {
	args: { state: readyPeople(peopleOf(LARGE_PEOPLE)) },
	play: async ({ canvas, userEvent }) => {
		const table = canvas.getByRole("table", { name: "People" });
		await expect(rows(table)).toHaveLength(50);
		await userEvent.click(canvas.getByRole("button", { name: "Show more people" }));
		await expect(rows(table)).toHaveLength(100);
	},
};

export const Empty: Story = {
	args: { state: readyPeople(peopleOf([])) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No activity in this range")).toBeVisible();
	},
};

/** With repositories picked, the empty table names its scope. */
export const EmptyForRepositories: Story = {
	args: { state: readyPeople(peopleOf([])), repo: ["hephaestus-build/Hephaestus"] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No activity in these repositories in this range")).toBeVisible();
	},
};

/** The previous range's figures stand in drained of their state colours, never as the new range's. */
export const Stale: Story = {
	args: { state: { status: "ready", people: peopleOf(PEOPLE), stale: true } },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "People" });
		await expect(table).toHaveAttribute("aria-busy", "true");
		await expect(getComputedStyle(table).filter).toContain("grayscale");
	},
};

/** An empty result that stands in while the next range loads is marked as the previous one's. */
export const StaleEmpty: Story = {
	args: { state: { status: "ready", people: peopleOf([]), stale: true } },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("No activity in this range").closest("[aria-busy='true']"),
		).not.toBeNull();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "People" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
		await expect(canvas.queryAllByRole("link")).toHaveLength(0);
	},
};

const onRetry = fn();

export const Failed: Story = {
	args: { state: { status: "error", error: new Error("Network down"), onRetry } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Retry/u }));
		await expect(onRetry).toHaveBeenCalledOnce();
	},
};

export const Dark: Story = { globals: { theme: "dark" } };

/** GitLab's own icons and colours: Pajamas merge requests, merged in blue. */
export const GitLab: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("columnheader", { name: /Merge requests/u })).toBeVisible();
		await expect(canvas.getAllByRole("img", { name: /merged$/u }).length).toBeGreaterThan(0);
	},
};

export const GitLabDark: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB" },
	globals: { theme: "dark" },
};

const LONG_NAME = "Maximiliane Alexandra Featherstonehaugh-Wolfeschlegelsteinhausen";
const TWO_WORD_NAME = "Konstantin Abernathy";
const large = peopleOf(LARGE_PEOPLE);
const [longFirst, twoWordSecond] = [...large.people].sort(
	(a, b) => b.counts.contributions - a.counts.contributions,
);

function renamed(row: (typeof large.people)[number]) {
	if (row === longFirst) {
		return { ...row, person: { ...row.person, name: LONG_NAME } };
	}
	if (row === twoWordSecond) {
		return { ...row, person: { ...row.person, name: TWO_WORD_NAME } };
	}
	return row;
}

/**
 * In a narrow column the figures scroll under the pinned position and name. Past row 100 a position has
 * three digits. A name wraps to two lines in its set width, so a first and last name read whole, and
 * a longer one with the New marker is cut after the second line.
 */
export const NarrowScroll: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	args: {
		state: readyPeople({
			...large,
			people: large.people.map(renamed),
			highlights: {
				...large.highlights,
				firstContributors: longFirst ? [longFirst.person.id] : [],
			},
		}),
	},
	play: async ({ canvas, userEvent }) => {
		const table = canvas.getByRole("table", { name: "People" });
		await userEvent.click(canvas.getByRole("button", { name: "Show more people" }));
		await userEvent.click(canvas.getByRole("button", { name: "Show more people" }));
		const container = table.closest<HTMLElement>("[data-slot='table-container']");
		await expect(container).not.toBeNull();
		if (!container) {
			return;
		}
		container.scrollLeft = container.scrollWidth;
		await waitFor(async () => expect(container.scrollLeft).toBeGreaterThan(0));
		await expect(window.innerWidth).toBe(320);

		const bodyRows = within(table).getAllByRole("row").slice(1);
		const threeDigits = bodyRows.find(
			(row) => (within(row).getAllByRole("cell")[0]?.textContent ?? "").length === 3,
		);
		await expect(threeDigits).toBeDefined();
		if (!threeDigits) {
			throw new Error("The rendered rows did not include a three-digit position");
		}
		const longRow = within(table).getByRole("row", { name: new RegExp(LONG_NAME, "u") });
		for (const row of [threeDigits, longRow]) {
			const [position, name] = within(row).getAllByRole("cell");
			if (!position || !name) {
				throw new Error("A row without its position or name");
			}
			row.scrollIntoView({ block: "center" });
			const box = container.getBoundingClientRect();
			const positionBox = position.getBoundingClientRect();
			const nameBox = name.getBoundingClientRect();
			// Identity stays at the edge and leaves room for the scrolling figures.
			await expect(Math.abs(positionBox.left - box.left)).toBeLessThanOrEqual(2);
			await expect(positionBox.right).toBeLessThanOrEqual(nameBox.left + 1);
			await expect(nameBox.right - box.left).toBeLessThanOrEqual(box.width * 0.75);
			// What a pointer at the name's centre hits is the name's cell, not a figure passing under it.
			const hit = document.elementFromPoint(
				nameBox.left + nameBox.width / 2,
				nameBox.top + nameBox.height / 2,
			);
			await expect(name.contains(hit)).toBe(true);
		}
		const link = within(longRow).getByRole("link", { name: LONG_NAME });
		const lineHeight = Number.parseFloat(getComputedStyle(link).lineHeight);
		await expect(link.clientHeight).toBeLessThanOrEqual(2 * lineHeight + 1);
		await expect(link.clientHeight).toBeGreaterThan(lineHeight + 1);
		await expect(link.scrollHeight).toBeGreaterThan(link.clientHeight);
		const twoWord = within(table).getByRole("link", { name: TWO_WORD_NAME });
		await expect(twoWord.clientHeight).toBeGreaterThan(lineHeight + 1);
		await expect(twoWord.scrollHeight).toBeLessThanOrEqual(twoWord.clientHeight + 1);
		await expect(
			within(longRow).getByRole("img", { name: "First contribution in this range" }),
		).toBeVisible();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPageOverflow();
	},
};
