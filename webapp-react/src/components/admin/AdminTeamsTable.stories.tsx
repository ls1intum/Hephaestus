import type { TeamInfo, UserTeams } from "@/api/types.gen";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { AdminTeamsTable } from "./AdminTeamsTable";

const mockRepositories = [
	{
		id: 1,
		name: "hephaestus",
		nameWithOwner: "awesome-org/hephaestus",
		description:
			"Your team's code review analytics platform - making reviews fun! 🚀",
		htmlUrl: "https://github.com/awesome-org/hephaestus",
	},
	{
		id: 2,
		name: "web-app",
		nameWithOwner: "awesome-org/web-app",
		description: "Beautiful React frontend that users absolutely love ❤️",
		htmlUrl: "https://github.com/awesome-org/web-app",
	},
	{
		id: 3,
		name: "mobile-app",
		nameWithOwner: "awesome-org/mobile-app",
		description:
			"Cross-platform mobile app bringing joy to users everywhere 📱",
		htmlUrl: "https://github.com/awesome-org/mobile-app",
	},
	{
		id: 4,
		name: "api-server",
		nameWithOwner: "awesome-org/api-server",
		description: "Rock-solid backend API powering all the magic ⚡",
		htmlUrl: "https://github.com/awesome-org/api-server",
	},
	{
		id: 5,
		name: "design-system",
		nameWithOwner: "awesome-org/design-system",
		description:
			"Consistent, accessible components that make development a breeze 🎨",
		htmlUrl: "https://github.com/awesome-org/design-system",
	},
];

const mockLabels = [
	{
		id: 101,
		name: "bug",
		color: "d73a49",
		repository: mockRepositories[0],
	},
	{
		id: 102,
		name: "enhancement",
		color: "a2eeef",
		repository: mockRepositories[0],
	},
	{
		id: 103,
		name: "frontend",
		color: "0075ca",
		repository: mockRepositories[1],
	},
	{
		id: 104,
		name: "backend",
		color: "28a745",
		repository: mockRepositories[3],
	},
	{
		id: 105,
		name: "urgent",
		color: "b60205",
		repository: mockRepositories[1],
	},
	{
		id: 106,
		name: "mobile",
		color: "fbca04",
		repository: mockRepositories[2],
	},
	{
		id: 107,
		name: "design",
		color: "e1a8c8",
		repository: mockRepositories[4],
	},
	{
		id: 108,
		name: "security",
		color: "5319e7",
		repository: mockRepositories[3],
	},
];

const mockTeams: TeamInfo[] = [
	{
		id: 1,
		name: "Frontend Champions",
		color: "#3b82f6",
		hidden: false,
		repositories: [
			mockRepositories[0],
			mockRepositories[1],
			mockRepositories[4],
		],
		labels: [mockLabels[0], mockLabels[2], mockLabels[6]],
		members: [
			{
				id: 1,
				login: "sarah-frontend",
				email: "sarah@awesome-org.com",
				avatarUrl: "https://github.com/sarah-frontend.png",
				name: "Sarah Frontend",
				htmlUrl: "https://github.com/sarah-frontend",
				leaguePoints: 245,
			},
			{
				id: 2,
				login: "mike-react",
				email: "mike@awesome-org.com",
				avatarUrl: "https://github.com/mike-react.png",
				name: "Mike React",
				htmlUrl: "https://github.com/mike-react",
				leaguePoints: 189,
			},
		],
	},
	{
		id: 2,
		name: "Backend Heroes",
		color: "#ef4444",
		hidden: false,
		repositories: [mockRepositories[0], mockRepositories[3]],
		labels: [mockLabels[1], mockLabels[3], mockLabels[7]],
		members: [
			{
				id: 3,
				login: "alex-api",
				email: "alex@awesome-org.com",
				avatarUrl: "https://github.com/alex-api.png",
				name: "Alex API",
				htmlUrl: "https://github.com/alex-api",
				leaguePoints: 312,
			},
		],
	},
	{
		id: 3,
		name: "DevOps Wizards",
		color: "#10b981",
		hidden: false,
		repositories: [mockRepositories[0], mockRepositories[3]],
		labels: [mockLabels[7]],
		members: [
			{
				id: 4,
				login: "jordan-deploy",
				email: "jordan@awesome-org.com",
				avatarUrl: "https://github.com/jordan-deploy.png",
				name: "Jordan Deploy",
				htmlUrl: "https://github.com/jordan-deploy",
				leaguePoints: 278,
			},
			{
				id: 5,
				login: "casey-cloud",
				email: "casey@awesome-org.com",
				avatarUrl: "https://github.com/casey-cloud.png",
				name: "Casey Cloud",
				htmlUrl: "https://github.com/casey-cloud",
				leaguePoints: 156,
			},
		],
	},
	{
		id: 4,
		name: "QA Guardians",
		color: "#f59e0b",
		hidden: true,
		repositories: [
			mockRepositories[0],
			mockRepositories[1],
			mockRepositories[2],
		],
		labels: [mockLabels[0], mockLabels[4]],
		members: [
			{
				id: 6,
				login: "taylor-test",
				email: "taylor@awesome-org.com",
				avatarUrl: "https://github.com/taylor-test.png",
				name: "Taylor Test",
				htmlUrl: "https://github.com/taylor-test",
				leaguePoints: 203,
			},
		],
	},
	{
		id: 5,
		name: "Design Squad",
		color: "#8b5cf6",
		hidden: false,
		repositories: [mockRepositories[4]],
		labels: [mockLabels[6]],
		members: [
			{
				id: 7,
				login: "river-creative",
				email: "river@awesome-org.com",
				avatarUrl: "https://github.com/river-creative.png",
				name: "River Creative",
				htmlUrl: "https://github.com/river-creative",
				leaguePoints: 167,
			},
			{
				id: 8,
				login: "morgan-design",
				email: "morgan@awesome-org.com",
				avatarUrl: "https://github.com/morgan-design.png",
				name: "Morgan Design",
				htmlUrl: "https://github.com/morgan-design",
				leaguePoints: 142,
			},
		],
	},
];

const mockUsers: UserTeams[] = [
	{
		id: 1,
		login: "alice-johnson",
		name: "Alice Johnson",
		url: "https://github.com/alice-johnson",
		teams: [mockTeams[0], mockTeams[2]],
	},
	{
		id: 2,
		login: "bob-smith",
		name: "Bob Smith",
		url: "https://github.com/bob-smith",
		teams: [mockTeams[1]],
	},
	{
		id: 3,
		login: "charlie-brown",
		name: "Charlie Brown",
		url: "https://github.com/charlie-brown",
		teams: [mockTeams[0], mockTeams[1], mockTeams[3]],
	},
	{
		id: 4,
		login: "diana-prince",
		name: "Diana Prince",
		url: "https://github.com/diana-prince",
		teams: [mockTeams[4]],
	},
	{
		id: 5,
		login: "ethan-hunt",
		name: "Ethan Hunt",
		url: "https://github.com/ethan-hunt",
		teams: [mockTeams[2], mockTeams[4]],
	},
];

/**
 * AdminTeamsTable provides a comprehensive interface for managing teams within your workspace.
 * Teams help organize repositories, labels, and members, making collaboration more effective.
 * Perfect for administrators who need to maintain team structures and permissions with ease! 🚀
 *
 * **New Features:**
 * - **Vertical Layout**: Teams are now displayed in a clean vertical stack for better readability
 * - **Horizontal Repositories**: Repository cards flow horizontally within each team
 * - **Popover Label Management**: Label management is now tucked away in convenient popovers
 * - **Compact Design**: More efficient use of screen space with better information density
 */
const meta = {
	component: AdminTeamsTable,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"A streamlined vertical interface for managing teams with horizontal repository cards and popover-based label management. Features intuitive search, effortless creation, smooth editing, and space-efficient team organization that makes collaboration a joy! ✨",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		teams: {
			description:
				"Array of team information including members, repositories, and labels - the heart of your workspace organization! 💼",
			control: false,
		},
		availableRepositories: {
			description:
				"Available repositories that can be assigned to teams - connect your codebase to the right people! 📁",
			control: false,
		},
		users: {
			description:
				"User information with team memberships - see who's part of which teams at a glance! 👥",
			control: false,
		},
		isLoading: {
			description:
				"Shows elegant loading skeleton when data is being fetched - keeps users informed and engaged! ⏳",
			control: "boolean",
		},
		onCreateTeam: {
			description:
				"Callback fired when creating a new team with name and color - bring new teams to life! ✨",
			action: "createTeam",
		},
		onDeleteTeam: {
			description:
				"Callback fired when deleting a team - handle team removal with care! 🗑️",
			action: "deleteTeam",
		},
		onHideTeam: {
			description:
				"Callback fired when toggling team visibility (hidden/visible) - control who sees what! 👁️",
			action: "hideTeam",
		},
		onUpdateTeam: {
			description:
				"Callback fired when updating team details (name/color) - keep teams fresh and relevant! 🎨",
			action: "updateTeam",
		},
		onAddRepositoryToTeam: {
			description:
				"Callback fired when adding a repository to a team - expand team responsibilities! 📚",
			action: "addRepositoryToTeam",
		},
		onRemoveRepositoryFromTeam: {
			description:
				"Callback fired when removing a repository from a team - refine team focus! 📝",
			action: "removeRepositoryFromTeam",
		},
		onAddLabelToTeam: {
			description:
				"Callback fired when adding a label to a team - enhance organization with smart tagging! 🏷️",
			action: "addLabelToTeam",
		},
		onRemoveLabelFromTeam: {
			description:
				"Callback fired when removing a label from a team - keep labeling clean and purposeful! ✂️",
			action: "removeLabelFromTeam",
		},
	},
	args: {
		teams: mockTeams,
		users: mockUsers,
		availableRepositories: mockRepositories,
		onCreateTeam: fn(),
		onDeleteTeam: fn(),
		onHideTeam: fn(),
		onUpdateTeam: fn(),
		onAddRepositoryToTeam: fn(),
		onRemoveRepositoryFromTeam: fn(),
		onAddLabelToTeam: fn(),
		onRemoveLabelFromTeam: fn(),
	},
} satisfies Meta<typeof AdminTeamsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * **Your workspace in action! 🎯**
 *
 * Default view showing a full workspace with multiple teams, each with their own repositories,
 * labels, and members. This represents a typical active development environment where
 * collaboration flourishes and teams work together seamlessly. Perfect for showcasing
 * how organized teams can make development more enjoyable and productive!
 */
export const Default: Story = {
	args: {},
};

/**
 * **Patience makes perfect! ⏳**
 *
 * Loading state with elegant skeleton placeholders while team data is being fetched.
 * Shows a professional loading experience that maintains layout stability and keeps
 * users informed. Great UX means users never feel left in the dark!
 */
export const Loading: Story = {
	args: {
		isLoading: true,
		teams: [],
	},
};

/**
 * **Every journey starts with a single step! 🌱**
 *
 * Empty state when no teams have been created yet. Encourages users to create
 * their first team with helpful messaging and clear call-to-action. This friendly
 * state helps new administrators get started confidently on their team management journey!
 */
export const EmptyState: Story = {
	args: {
		teams: [],
		users: [],
		isLoading: false,
	},
};

/**
 * **Starting small and growing strong! 🚀**
 *
 * Single team view perfect for new workspaces or focused team management.
 * Shows how individual team cards display repository and label information clearly.
 * Sometimes less is more - this view helps you focus on getting one team right!
 */
export const SingleTeam: Story = {
	args: {
		teams: [mockTeams[0]],
		users: mockUsers.slice(0, 2),
	},
};

/**
 * **Clean slate, infinite possibilities! ✨**
 *
 * Teams without any repositories or members, useful for demonstrating
 * the clean state before teams are fully configured. Shows the potential
 * waiting to be unlocked - these teams are ready for their first members and repos!
 */
export const TeamsWithoutRepositories: Story = {
	args: {
		teams: [
			{
				id: 1,
				name: "New Frontend Team",
				color: "#6366f1",
				hidden: false,
				repositories: [],
				labels: [],
				members: [],
			},
			{
				id: 2,
				name: "Upcoming Backend Team",
				color: "#ef4444",
				hidden: false,
				repositories: [],
				labels: [],
				members: [],
			},
		],
		users: [],
	},
};

/**
 * **Scaling with confidence! 📈**
 *
 * Large workspace with many teams to test performance and responsive layout.
 * Demonstrates how the grid adapts beautifully to accommodate numerous teams.
 * When your organization grows, the interface grows with you - that's smart design!
 */
export const ManyTeams: Story = {
	args: {
		teams: Array.from({ length: 15 }, (_, i) => ({
			id: i + 1,
			name: `Team ${["Alpha", "Beta", "Gamma", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet", "Kilo", "Lima", "Mike", "November", "Oscar"][i] || `${i + 1}`}`,
			color: `hsl(${(i * 137.5) % 360}, 65%, 50%)`,
			hidden: i % 8 === 0,
			repositories:
				i % 3 === 0 ? [mockRepositories[i % mockRepositories.length]] : [],
			labels: i % 4 === 0 ? [mockLabels[i % mockLabels.length]] : [],
			members: [],
		})),
		users: mockUsers,
	},
};

/**
 * **Privacy when you need it! 🕶️**
 *
 * Teams with hidden visibility to demonstrate the administrative capability
 * of managing team visibility and the visual treatment of hidden teams.
 * Sometimes teams need to work behind the scenes - this shows how gracefully hidden teams are handled!
 */
export const HiddenTeams: Story = {
	args: {
		teams: mockTeams.map((team) => ({ ...team, hidden: true })),
		users: mockUsers,
	},
};

/**
 * **Label management in a popover! 🏷️**
 *
 * Interactive demonstration of the new popover-based label management system.
 * Click the settings icon (⚙️) on any repository card to open the label management popover.
 * Add labels with the "Add Label" button and remove them with the × button. Clean and focused!
 */
export const InteractiveLabelManagement: Story = {
	args: {
		teams: mockTeams,
		users: mockUsers,
		onAddLabelToTeam: fn(async (teamId, repositoryId, labelName) => {
			console.log(
				`Adding label "${labelName}" to team ${teamId} for repository ${repositoryId}`,
			);
			// Simulate API delay
			await new Promise((resolve) => setTimeout(resolve, 500));
		}),
		onRemoveLabelFromTeam: fn(async (teamId, labelId) => {
			console.log(`Removing label ${labelId} from team ${teamId}`);
		}),
	},
	parameters: {
		docs: {
			description: {
				story: `This story demonstrates the enhanced label management system with a proper input field for custom label creation.
				
**Key Features:**
- **Popover Interface**: Click the settings icon (⚙️) on repository cards to manage labels
- **Custom Label Input**: Type your own label names (e.g., "bug", "feature", "docs") in the input field
- **Enter Key Support**: Press Enter to quickly add labels without clicking the button
- **Loading States**: The button shows a spinner while adding labels (simulated API delay)
- **Form Validation**: The add button is disabled when the input is empty
- **Space Efficient**: Label management is hidden until needed, keeping the interface clean
- **GitHubBadge Integration**: Labels use authentic GitHub styling within the popover
- **Easy Removal**: Click the × button on any label to remove it
- **Preview Display**: Main view shows up to 3 labels with a "+X more" indicator

Try typing custom label names in the input field and watch the smooth interactions with loading states!`,
			},
		},
	},
};

/**
 * **GitHub label showcase! 🎨**
 *
 * Teams with diverse GitHub-style labels to demonstrate the visual variety and
 * authentic styling of the GitHubBadge component across different colors and themes.
 */
export const RichLabelShowcase: Story = {
	args: {
		teams: [
			{
				...mockTeams[0],
				name: "Frontend Innovators",
				labels: [
					{
						id: 101,
						name: "enhancement",
						color: "a2eeef",
						repository: mockRepositories[0],
					},
					{
						id: 102,
						name: "good first issue",
						color: "7057ff",
						repository: mockRepositories[0],
					},
					{
						id: 103,
						name: "javascript",
						color: "f1e05a",
						repository: mockRepositories[1],
					},
					{
						id: 104,
						name: "react",
						color: "61dafb",
						repository: mockRepositories[1],
					},
				],
			},
			{
				...mockTeams[1],
				name: "Backend Experts",
				labels: [
					{
						id: 105,
						name: "bug",
						color: "d73a4a",
						repository: mockRepositories[0],
					},
					{
						id: 106,
						name: "priority: high",
						color: "ff8800",
						repository: mockRepositories[0],
					},
					{
						id: 107,
						name: "api",
						color: "0052cc",
						repository: mockRepositories[3],
					},
					{
						id: 108,
						name: "database",
						color: "5319e7",
						repository: mockRepositories[3],
					},
				],
			},
			{
				...mockTeams[2],
				name: "DevOps Champions",
				labels: [
					{
						id: 109,
						name: "infrastructure",
						color: "0e8a16",
						repository: mockRepositories[0],
					},
					{
						id: 110,
						name: "docker",
						color: "2496ed",
						repository: mockRepositories[0],
					},
					{
						id: 111,
						name: "kubernetes",
						color: "326ce5",
						repository: mockRepositories[3],
					},
					{
						id: 112,
						name: "monitoring",
						color: "e99695",
						repository: mockRepositories[3],
					},
				],
			},
		],
		users: mockUsers.slice(0, 3),
	},
	parameters: {
		docs: {
			description: {
				story: `Showcases the beautiful variety of GitHub-style labels with authentic colors and styling.
				
**Label Categories Shown:**
- **Enhancement & Features**: Light blue and purple labels for new functionality
- **Bug Reports**: Classic red labels for issues and high-priority items  
- **Technology Tags**: Language and framework-specific colored labels
- **Infrastructure**: Green and blue labels for DevOps and system components

Each label maintains GitHub's exact color contrast and visual hierarchy for optimal readability.`,
			},
		},
	},
};

/**
 * **Label-focused teams! 📋**
 *
 * Teams organized specifically around different types of labels and repositories
 * to demonstrate how labels help categorize and organize development work effectively.
 */
export const LabelOrganizedTeams: Story = {
	args: {
		teams: [
			{
				id: 201,
				name: "Bug Hunters",
				color: "#dc2626",
				hidden: false,
				repositories: [mockRepositories[0], mockRepositories[1]],
				labels: [
					{
						id: 201,
						name: "bug",
						color: "d73a4a",
						repository: mockRepositories[0],
					},
					{
						id: 202,
						name: "critical",
						color: "b60205",
						repository: mockRepositories[0],
					},
					{
						id: 203,
						name: "regression",
						color: "e99695",
						repository: mockRepositories[1],
					},
				],
				members: mockTeams[0].members.slice(0, 1),
			},
			{
				id: 202,
				name: "Feature Builders",
				color: "#2563eb",
				hidden: false,
				repositories: [mockRepositories[1], mockRepositories[4]],
				labels: [
					{
						id: 204,
						name: "enhancement",
						color: "a2eeef",
						repository: mockRepositories[1],
					},
					{
						id: 205,
						name: "feature request",
						color: "7057ff",
						repository: mockRepositories[1],
					},
					{
						id: 206,
						name: "user experience",
						color: "c5def5",
						repository: mockRepositories[4],
					},
				],
				members: mockTeams[1].members,
			},
			{
				id: 203,
				name: "Documentation Squad",
				color: "#059669",
				hidden: false,
				repositories: [mockRepositories[2]],
				labels: [
					{
						id: 207,
						name: "documentation",
						color: "0075ca",
						repository: mockRepositories[2],
					},
					{
						id: 208,
						name: "help wanted",
						color: "008672",
						repository: mockRepositories[2],
					},
					{
						id: 209,
						name: "good first issue",
						color: "7057ff",
						repository: mockRepositories[2],
					},
				],
				members: mockTeams[2].members.slice(0, 1),
			},
		],
		users: mockUsers.slice(0, 3),
	},
	parameters: {
		docs: {
			description: {
				story: `Demonstrates teams organized around specific types of work, each with relevant GitHub labels.

**Team Specializations:**
- **Bug Hunters**: Focus on critical issues and regressions with red-themed labels
- **Feature Builders**: Drive new functionality with blue and purple enhancement labels  
- **Documentation Squad**: Maintain project docs with blue-green informational labels

This organization pattern helps teams maintain clear focus areas while using labels as visual indicators of work type.`,
			},
		},
	},
};
