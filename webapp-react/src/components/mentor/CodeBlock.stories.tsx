import type { Meta, StoryObj } from "@storybook/react";
import { CodeBlock } from "./CodeBlock";

/**
 * CodeBlock component for displaying code snippets in chat responses.
 * Supports inline code and multi-line blocks with dark mode and overflow handling.
 */
const meta = {
	component: CodeBlock,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		inline: {
			control: "boolean",
			description: "Whether to display as inline code or a code block",
		},
	},
	args: {
		inline: false,
		className: "",
		children: 'console.log("Hello, world!");',
	},
	decorators: [
		(Story) => (
			<div className="max-w-4xl p-6 bg-background">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof CodeBlock>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default multi-line code block for code snippets.
 */
export const Default: Story = {};

/**
 * Inline code for referencing variables within text.
 */
export const Inline: Story = {
	args: {
		inline: true,
		children: "useState",
	},
	decorators: [
		(Story) => (
			<p className="text-foreground">
				The <Story /> hook is essential for managing component state in React.
			</p>
		),
	],
};

/**
 * TypeScript function demonstrating proper formatting and scrolling.
 */
export const TypeScriptFunction: Story = {
	args: {
		className: "language-typescript",
		children: `function calculateLeaguePoints(reviews: Review[]): number {
  const basePoints = reviews.length * 10;
  const qualityBonus = reviews
    .filter(r => r.rating >= 4)
    .length * 5;
  
  return basePoints + qualityBonus;
}`,
	},
};

/**
 * Terminal output with command and response formatting.
 */
export const TerminalOutput: Story = {
	args: {
		className: "language-bash",
		children: `$ npm run build

✓ TypeScript compilation successful
✓ 42 modules transformed
dist/index.html                 0.45 kB
dist/assets/index-a1b2c3d4.js  145.23 kB │ gzip: 46.12 kB
✓ built in 1.34s`,
	},
};

/**
 * Long code demonstrating horizontal scrolling behavior.
 */
export const LongCodeLine: Story = {
	args: {
		className: "language-javascript",
		children: `// This line demonstrates horizontal scrolling when content exceeds container width
const veryLongVariableNameThatDemonstratesHorizontalScrollingBehaviorInCodeBlocks = await fetchUserProfileDataWithExtensiveErrorHandlingAndRetryLogic();`,
	},
};

/**
 * JSON API response with proper formatting.
 */
export const JSONResponse: Story = {
	args: {
		className: "language-json",
		children: `{
  "user": {
    "id": 12345,
    "login": "johndoe",
    "name": "John Doe"
  },
  "leaguePoints": 1250,
  "rank": 42,
  "recentActivity": [
    {
      "type": "review",
      "repository": "webapp-react",
      "points": 15
    }
  ]
}`,
	},
};

/**
 * Shows typical AI mentor conversation context with mixed inline and block code.
 */
export const InMentorContext: Story = {
	args: {
		inline: false,
		className: "language-typescript",
		children: `async function handleApiError(error: unknown) {
  if (error instanceof Response) {
    const message = await error.text();
    console.error('API Error:', message);
  }
  throw error;
}`,
	},
	render: () => (
		<div className="space-y-4 max-w-4xl">
			<div className="text-foreground">
				<p className="mb-4">
					Great question! Here's how you can implement proper error handling in
					your API calls:
				</p>
				<CodeBlock inline={false} className="language-typescript">
					{`async function handleApiError(error: unknown) {
  if (error instanceof Response) {
    const message = await error.text();
    console.error('API Error:', message);
  }
  throw error;
}`}
				</CodeBlock>
				<p className="mt-4">
					The key is to check{" "}
					<CodeBlock inline className="">
						error instanceof Response
					</CodeBlock>{" "}
					before trying to extract the error message. This pattern works well
					with React Query too!
				</p>
			</div>
		</div>
	),
	parameters: {
		docs: {
			description: {
				story:
					"Demonstrates the component in typical AI mentor conversation flow.",
			},
		},
	},
};

/**
 * TypeScript code with syntax highlighting demonstration.
 */
export const TypeScriptSyntax: Story = {
	args: {
		inline: false,
		className: "language-typescript",
		children: `interface UserProfile {
  id: string;
  name: string;
  email: string;
  leaguePoints: number;
}

async function fetchUserProfile(userId: string): Promise<UserProfile> {
  const response = await fetch(\`/api/users/\${userId}\`);
  if (!response.ok) {
    throw new Error(\`Failed to fetch user: \${response.status}\`);
  }
  return response.json();
}

// Usage example
const user = await fetchUserProfile('user-123');
console.log(\`Welcome \${user.name}! You have \${user.leaguePoints} points.\`);`,
	},
};

/**
 * JSON configuration with syntax highlighting.
 */
export const JSONSyntax: Story = {
	args: {
		inline: false,
		className: "language-json",
		children: `{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "strict": true,
    "jsx": "react-jsx"
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist"]
}`,
	},
};
