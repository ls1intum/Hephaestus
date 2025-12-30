import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { BadPracticeCard } from "./BadPracticeCard";

/**
 * Card component that displays coding practices with their state and information.
 * Allows users to mark bad practices as fixed.
 */
const meta = {
	component: BadPracticeCard,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A card displaying a coding practice with contextual styling based on its current state.",
			},
		},
	},
	argTypes: {
		id: {
			description: "Unique identifier for the practice",
			control: "number",
		},
		title: {
			description: "Title of the practice",
			control: "text",
		},
		description: {
			description: "Detailed description of the practice",
			control: "text",
		},
		state: {
			description: "Current state of the practice",
			control: "select",
			options: [
				"GOOD_PRACTICE",
				"FIXED",
				"CRITICAL_ISSUE",
				"NORMAL_ISSUE",
				"MINOR_ISSUE",
				"WONT_FIX",
				"WRONG",
			],
		},
		onResolveBadPracticeAsFixed: {
			description: "Callback when a bad practice is marked as fixed",
			action: "resolved as fixed",
		},
	},
	args: {
		onResolveBadPracticeAsFixed: fn(),
	},
} satisfies Meta<typeof BadPracticeCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Example of a recommended coding practice displayed in a positive state.
 */
export const GoodPractice: Story = {
	args: {
		id: 1,
		title: "Avoid using any type",
		description:
			"Using the any type defeats the purpose of TypeScript's static typing. Use more specific types instead.",
		state: "GOOD_PRACTICE",
	},
};

/**
 * Example of a practice that has been fixed by the user.
 */
export const Fixed: Story = {
	args: {
		id: 2,
		title: "Avoid using any type",
		description:
			"Using the any type defeats the purpose of TypeScript's static typing. Use more specific types instead.",
		state: "FIXED",
	},
};

/**
 * Example of a normal issue that needs to be addressed by the user.
 */
export const NormalIssue: Story = {
	args: {
		id: 3,
		title: "Avoid nested callbacks",
		description:
			"Deeply nested callbacks create 'callback hell' and make code difficult to read and maintain. Use async/await or Promise chaining instead.",
		state: "NORMAL_ISSUE",
	},
};

/**
 * Example of a critical issue that requires immediate attention.
 */
export const CriticalIssue: Story = {
	args: {
		id: 4,
		title: "Fix security vulnerability",
		description:
			"This code contains a potential SQL injection vulnerability that could allow attackers to access sensitive data.",
		state: "CRITICAL_ISSUE",
	},
};

export const MinorIssue: Story = {
	args: {
		id: 5,
		title: "Avoid using any type",
		description: "Using the any type defeats the purpose of TypeScript.",
		state: "MINOR_ISSUE",
	},
};

export const WontFix: Story = {
	args: {
		id: 6,
		title: "Avoid using any type",
		description: "Using the any type defeats the purpose of TypeScript.",
		state: "WONT_FIX",
	},
};

export const Wrong: Story = {
	args: {
		id: 7,
		title: "Avoid using any type",
		description: "Using the any type defeats the purpose of TypeScript.",
		state: "WRONG",
	},
};

export const WithResolutionControls: Story = {
	args: {
		id: 8,
		title: "Avoid using any type",
		description: "Using the any type defeats the purpose of TypeScript.",
		state: "NORMAL_ISSUE",
		currUserIsDashboardUser: true,
	},
};

/**
 * Demonstrates that resolved items (FIXED state) do NOT show the Resolve button,
 * even when the user is a dashboard user. This prevents confusion from showing
 * resolution options for already-resolved practices.
 */
export const ResolvedWithDashboardUser: Story = {
	args: {
		id: 9,
		title: "Previously flagged issue",
		description: "This issue was already resolved as fixed.",
		state: "FIXED",
		currUserIsDashboardUser: true,
	},
};

/**
 * Demonstrates that WONT_FIX items do NOT show the Resolve button,
 * even for dashboard users.
 */
export const WontFixWithDashboardUser: Story = {
	args: {
		id: 10,
		title: "Issue marked as won't fix",
		description: "This issue was intentionally left unaddressed.",
		state: "WONT_FIX",
		currUserIsDashboardUser: true,
	},
};

/**
 * Demonstrates that good practices do NOT show the Resolve button,
 * as they don't require resolution.
 */
export const GoodPracticeWithDashboardUser: Story = {
	args: {
		id: 11,
		title: "Excellent code organization",
		description: "The code follows best practices for structure and naming.",
		state: "GOOD_PRACTICE",
		currUserIsDashboardUser: true,
	},
};
