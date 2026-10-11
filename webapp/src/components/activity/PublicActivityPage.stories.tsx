import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import {
	LARGE_PEOPLE,
	PEOPLE,
	publicActivityOf,
	readyPublicPeople,
	REPOSITORIES,
} from "@/stories/activity-story-data";
import { withProvider, withStandardPage } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPageOverflow } from "@/stories/reflow";
import { daysBefore } from "@/stories/story-clock";
import { precedes } from "@/test/dom";

import { parseActivityStack, PUBLIC_ACTIVITY_LEVEL_KINDS } from "./activity-search";
import { PublicActivityPage, PublicActivityPageSkeleton } from "./PublicActivityPage";

const activity = publicActivityOf(PEOPLE);

const meta = {
	component: PublicActivityPage,
	parameters: { layout: "fullscreen", chromatic: { viewports: [320, 1440] } },
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceName: activity.workspaceName,
		providerType: "GITHUB",
		viewer: { status: "signed-out", onSignIn: fn() },
		period: { kind: "preset", preset: "90d" },
		onPeriodChange: fn(),
		order: { sort: "contributions", desc: true },
		onOrderChange: fn(),
		repo: [],
		onRepoChange: fn(),
		repositories: activity.repositories,
		coverage: activity.coverage,
		people: readyPublicPeople(activity),
		updating: false,
		detail: { stack: [], onClose: fn() },
	},
} satisfies Meta<typeof PublicActivityPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** What a visitor sees: the people with their figures, and the way to hide oneself. */
export const SignedOut: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			canvas.getByRole("heading", { level: 1, name: "Hephaestus activity" }),
		).toBeVisible();
		await expect(
			canvas.getByText(
				/This page shows pull requests, reviews and issues in public repositories\./u,
			),
		).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Sign in" }));
		await expect(
			args.viewer.status === "signed-out" ? args.viewer.onSignIn : undefined,
		).toHaveBeenCalledOnce();
		await expect(canvas.getByRole("table", { name: "People" })).toBeVisible();
	},
};

/** A person on the page signs in, and finds the switch in User settings. */
export const SignedIn: Story = {
	args: { viewer: { status: "signed-in" } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: "Sign in" })).not.toBeInTheDocument();
		await expect(canvas.getByRole("link", { name: "User settings" })).toHaveAttribute(
			"href",
			"/settings",
		);
	},
};

/** Names open the published figures here; the provider profile is a separate link. */
export const PersonLinks: Story = {
	play: async ({ canvas }) => {
		const link = within(canvas.getByRole("table", { name: "People" })).getByRole("link", {
			name: "Ada Lovelace",
		});
		await expect(
			new URL(link.getAttribute("href") ?? "", window.location.href).searchParams.get("detail"),
		).toBe("person:ada");
		await expect(link).not.toHaveAttribute("target");
	},
};

export const Loading: Story = {
	args: { people: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "People" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
	},
};

/** The first read of the page: its shape, and nothing that stands in for the workspace or its people. */
export const FirstLoad: Story = {
	render: () => <PublicActivityPageSkeleton />,
	play: async ({ canvas }) => {
		canvas.getByText("Loading public activity");
		await expect(canvas.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Sign in" })).not.toBeInTheDocument();
		await expect(canvas.queryByRole("table", { name: "People" })).not.toBeInTheDocument();
	},
};

const onRetry = fn();

export const Failed: Story = {
	args: {
		workspaceName: undefined,
		coverage: undefined,
		repositories: [],
		people: { status: "error", error: new Error("Network down"), onRetry },
	},
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.getByRole("heading", { level: 1, name: "Public activity" })).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: /Retry/u }));
		await expect(onRetry).toHaveBeenCalledOnce();
	},
};

/** Nobody contributed to a public repository in the period. */
export const Empty: Story = {
	args: { people: readyPublicPeople(publicActivityOf([])) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No activity in this range")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Custom range" })).toBeVisible();
	},
};

/** Some repositories' history is complete: the page says since when, and for how many. */
export const PartialHistory: Story = {
	args: {
		coverage: { since: daysBefore(400), completeRepositories: 1, totalRepositories: 2 },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/History since .* for 1 of 2 repositories\./u)).toBeVisible();
	},
};

/** 250 people: the table renders 50 rows, and more as its end scrolls into view. */
export const LargeWorkspace: Story = {
	args: { people: readyPublicPeople(publicActivityOf(LARGE_PEOPLE)) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("250 people")).toBeVisible();
	},
};

export const OneRepository: Story = {
	args: { repo: [REPOSITORIES[0]?.key ?? ""], repositories: activity.repositories },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("combobox", { name: "Repository: hephaestus-build/Hephaestus" }),
		).toBeVisible();
	},
};

export const Dark: Story = { globals: { theme: "dark" } };

/** GitLab's own words, icons and colours: merge requests, projects, Pajamas icons. */
export const GitLab: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB" },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/This page shows merge requests, reviews and issues in public projects\./u),
		).toBeVisible();
		await expect(canvas.getByRole("columnheader", { name: /Merge requests/u })).toBeVisible();
	},
};

export const GitLabDark: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB" },
	globals: { theme: "dark" },
};

/** Wide screens keep the page to its readable width, so names and figures stay close. */
export const Wide: Story = {
	parameters: { chromatic: { viewports: [1920] } },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", { name: "People" });
		await expect(table.getBoundingClientRect().width).toBeLessThanOrEqual(1152);
	},
};

const adaDetail = {
	stack: parseActivityStack(["person:ada"], PUBLIC_ACTIVITY_LEVEL_KINDS),
	onClose: fn(),
};

export const PersonDetails: Story = {
	args: {
		detail: adaDetail,
		people: readyPublicPeople(
			publicActivityOf(PEOPLE, {
				from: new Date("2026-12-28T00:00:00Z"),
				to: new Date("2027-01-11T00:00:00Z"),
				people: activity.people.map((person) => ({
					...person,
					counts: { ...person.counts, activeWeeks: 2 },
					weeks: [
						{ start: new Date("2026-12-28T00:00:00Z"), contributions: person.counts.contributions },
						{ start: new Date("2027-01-04T00:00:00Z"), contributions: 0 },
					],
				})),
			}),
		),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("heading", { name: "Ada Lovelace" })).toBeVisible();
		await expect(panel.getByRole("heading", { name: "In this range" })).toBeVisible();
		await expect(panel.getByText("Distinct pull requests reviewed")).toBeVisible();
		const weeks = within(panel.getByRole("table", { name: "Contributions by week" }));
		await expect(weeks.getByRole("cell", { name: "28 December 2026" })).toBeVisible();
		await expect(weeks.getByRole("cell", { name: "4 January 2027" })).toBeVisible();
		await expect(
			precedes(
				panel.getByRole("link", { name: /ada on GitHub/u }),
				panel.getByRole("heading", { name: "In this range" }),
			),
		).toBe(true);
		await expect(panel.getByRole("link", { name: /ada on GitHub/u })).toHaveAttribute(
			"href",
			"https://github.com/ada",
		);
		await expect(panel.getByRole("link", { name: /ada on GitHub/u })).toHaveAttribute(
			"target",
			"_blank",
		);
	},
};

export const PersonMissing: Story = {
	args: { detail: adaDetail, people: readyPublicPeople(publicActivityOf([])) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText("No activity in this range")).toBeVisible();
		await expect(panel.queryByRole("link", { name: /on GitHub/u })).not.toBeInTheDocument();
	},
};

export const PersonLoading: Story = {
	args: { detail: adaDetail, people: { status: "loading" } },
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector('[aria-busy="true"]')).toBeVisible();
		await expect(within(panel).queryByRole("link", { name: /on GitHub/u })).not.toBeInTheDocument();
		await expect(
			within(panel).queryByRole("heading", { name: "In this range" }),
		).not.toBeInTheDocument();
		await expect(within(panel).queryByRole("table")).not.toBeInTheDocument();
	},
};

export const PersonFailed: Story = {
	args: {
		detail: adaDetail,
		people: { status: "error", error: new Error("Network down"), onRetry: fn() },
	},
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText("We could not load this person’s activity")).toBeVisible();
		await userEvent.click(panel.getByRole("button", { name: /Retry/u }));
		await expect(
			args.people.status === "error" ? args.people.onRetry : undefined,
		).toHaveBeenCalledOnce();
	},
};

export const PersonUpdating: Story = {
	args: { detail: adaDetail, period: { kind: "preset", preset: "30d" }, updating: true },
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(
			within(panel).getByText(/These figures are for .* while activity updates\./u),
		).toBeVisible();
		await expect(panel.querySelector('[aria-busy="true"]')).toBeVisible();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const RefreshFailed: Story = {
	args: { refreshError: { error: { status: 503 }, onRetry: fn(), pending: false } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not refresh activity")).toBeVisible();
		await expect(canvas.getByRole("table", { name: "People" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

export const RefreshRetrying: Story = {
	args: { refreshError: { error: { status: 503 }, onRetry: fn(), pending: true }, updating: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "People" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retrying…" })).toBeDisabled();
	},
};
