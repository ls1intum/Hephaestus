import type { Meta, StoryObj } from "@storybook/react";
import { Markdown } from "./Markdown";

/**
 * Markdown component for rendering AI mentor responses with full GFM support.
 * Supports all standard markdown plus GitHub Flavored Markdown extensions: tables, task lists, strikethrough, footnotes, and autolinks.
 */
const meta = {
	component: Markdown,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		children: {
			control: "text",
			description: "Markdown content to render",
		},
	},
	args: {
		children: "# Hello World\n\nThis is **markdown** with `inline code`.",
	},
	decorators: [
		(Story) => (
			<div className="max-w-4xl p-6 bg-background">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof Markdown>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Basic markdown with common formatting elements.
 */
export const Default: Story = {};

/**
 * Complete heading hierarchy from H1 to H6.
 */
export const Headings: Story = {
	args: {
		children: [
			"# Heading 1",
			"## Heading 2",
			"### Heading 3",
			"#### Heading 4",
			"##### Heading 5",
			"###### Heading 6",
			"",
			"Each heading level has distinct visual hierarchy for content organization.",
		].join("\n"),
	},
};

/**
 * Text formatting: bold, italic, inline code, and combinations.
 */
export const TextFormatting: Story = {
	args: {
		children: [
			"Here are the text formatting options:",
			"",
			"**Bold text** for emphasis and importance.",
			"",
			"*Italic text* for subtle emphasis.",
			"",
			"***Bold and italic*** for maximum emphasis.",
			"",
			"Inline code like `useState` and `useEffect` for technical terms.",
			"",
			"You can combine **bold with `code`** for technical documentation.",
		].join("\n"),
	},
};

/**
 * GitHub Flavored Markdown strikethrough syntax.
 */
export const Strikethrough: Story = {
	args: {
		children: [
			"## Strikethrough Examples",
			"",
			"~~Single strikethrough~~ for crossed-out text.",
			"",
			"~Alternative syntax~ also works.",
			"",
			"Use it for ~~deprecated APIs~~ or ~~old approaches~~.",
			"",
			"**Example:** Use `useQuery` instead of ~~`useEffect` for data fetching~~.",
		].join("\n"),
	},
};

/**
 * Tables with different alignment options.
 */
export const Tables: Story = {
	args: {
		children: [
			"## Performance Comparison",
			"",
			"| Framework | Bundle Size | Performance | Learning Curve |",
			"|-----------|------------|-------------|----------------|",
			"| React | 42.2kb | Excellent | Moderate |",
			"| Vue | 39.1kb | Excellent | Easy |",
			"| Angular | 130kb | Good | Steep |",
			"| Svelte | 10.3kb | Excellent | Easy |",
			"",
			"## Alignment Examples",
			"",
			"| Left Aligned | Center Aligned | Right Aligned |",
			"|:-------------|:--------------:|--------------:|",
			"| React | **Popular** | 220k+ ⭐ |",
			"| Vue | **Growing** | 207k+ ⭐ |",
			"| Angular | **Enterprise** | 93k+ ⭐ |",
		].join("\n"),
	},
};

/**
 * Task lists for interactive checkboxes.
 */
export const TaskLists: Story = {
	args: {
		children: [
			"## Code Review Checklist",
			"",
			"- [x] Code follows project style guidelines",
			"- [x] All tests pass",
			"- [ ] Documentation is updated",
			"- [ ] Performance impact considered",
			"- [ ] Security review completed",
			"",
			"## Learning Plan",
			"",
			"- [x] Learn React basics",
			"- [x] Understand hooks",
			"- [ ] Master state management",
			"  - [x] useState and useReducer",
			"  - [ ] Context API",
			"  - [ ] External libraries (Zustand, Redux)",
			"- [ ] Advanced patterns",
			"- [ ] Performance optimization",
		].join("\n"),
	},
};

/**
 * Code blocks with syntax highlighting for different languages.
 */
export const CodeBlocks: Story = {
	args: {
		children: [
			"## TypeScript API Example",
			"",
			"```typescript",
			"interface UserProfile {",
			"  id: string;",
			"  name: string;",
			"  email: string;",
			"  leaguePoints: number;",
			"}",
			"",
			"async function fetchUserProfile(userId: string): Promise<UserProfile> {",
			"  const response = await fetch(`/api/users/${userId}`);",
			"  if (!response.ok) {",
			"    throw new Error(`Failed to fetch user: ${response.status}`);",
			"  }",
			"  return response.json();",
			"}",
			"```",
			"",
			"## Shell Commands",
			"",
			"```bash",
			"# Install dependencies",
			"npm install @tanstack/react-query",
			"",
			"# Run development server",
			"npm run dev",
			"",
			"# Build for production",
			"npm run build",
			"```",
			"",
			"## JSON Configuration",
			"",
			"```json",
			"{",
			'  "compilerOptions": {',
			'    "strict": true,',
			'    "target": "ES2022",',
			'    "module": "ESNext"',
			"  },",
			'  "include": ["src/**/*"]',
			"}",
			"```",
		].join("\n"),
	},
};

/**
 * Lists with proper nesting and different types.
 */
export const Lists: Story = {
	args: {
		children: [
			"## Ordered List",
			"",
			"1. **Setup Phase**",
			"   1. Initialize project",
			"   2. Install dependencies",
			"   3. Configure tools",
			"",
			"2. **Development Phase**",
			"   1. Write components",
			"   2. Add tests",
			"   3. Document features",
			"",
			"3. **Deployment Phase**",
			"   1. Build application",
			"   2. Run tests",
			"   3. Deploy to production",
			"",
			"## Unordered List",
			"",
			"- **Frontend Technologies**",
			"  - React with TypeScript",
			"  - TailwindCSS for styling",
			"  - Storybook for documentation",
			"  ",
			"- **Backend Services**",
			"  - REST APIs",
			"  - Database integration",
			"  - Authentication",
			"",
			"- **DevOps & Tools**",
			"  - Git version control",
			"  - CI/CD pipelines",
			"  - Monitoring & analytics",
		].join("\n"),
	},
};

/**
 * Links with various formats and security attributes.
 */
export const Links: Story = {
	args: {
		children: [
			"## External Resources",
			"",
			"Essential documentation:",
			"",
			"- [React Documentation](https://react.dev) - Official React guide",
			"- [TypeScript Handbook](https://www.typescriptlang.org/docs/) - Learn TypeScript",
			"- [TailwindCSS](https://tailwindcss.com) - Utility-first CSS framework",
			"",
			"## Auto-linked URLs",
			"",
			"Visit https://github.com/facebook/react for the React repository.",
			"",
			"Contact us at support@example.com for help.",
			"",
			"Also check www.example.com for more resources.",
		].join("\n"),
	},
};

/**
 * Footnotes with references and definitions.
 */
export const Footnotes: Story = {
	args: {
		children: [
			"## React Performance Optimization",
			"",
			"React provides several optimization techniques[^1] for improving application performance.",
			"",
			"### Memoization",
			"",
			"Use `React.memo`[^2] for component-level optimization:",
			"",
			"```typescript",
			"const ExpensiveComponent = React.memo(({ data }) => {",
			"  return <div>{/* expensive rendering */}</div>;",
			"});",
			"```",
			"",
			"### Virtual DOM Benefits",
			"",
			"React's virtual DOM[^3] provides efficient updates by comparing tree differences.",
			"",
			"[^1]: Performance optimization is crucial for user experience and should be measured before implementing.",
			"",
			"[^2]: React.memo performs a shallow comparison of props. For deep comparison, provide a custom comparison function.",
			"",
			"[^3]: The virtual DOM is React's in-memory representation of the real DOM elements, enabling efficient batch updates.",
		].join("\n"),
	},
};

/**
 * Blockquotes for highlighting important information.
 */
export const Blockquotes: Story = {
	args: {
		children: [
			"## Important Notes",
			"",
			"> **Performance Tip:** Always measure before optimizing. Use React DevTools Profiler to identify actual bottlenecks.",
			"",
			"> ⚠️ **Warning:** Premature optimization is the root of all evil. Focus on functionality first.",
			"",
			"### Nested Blockquotes",
			"",
			"> **Best Practice Guidelines:**",
			">",
			"> > When writing React components, follow these principles:",
			"> > - Keep components small and focused",
			"> > - Use TypeScript for better DX",
			"> > - Write tests for critical functionality",
			">",
			"> These guidelines will help maintain code quality over time.",
		].join("\n"),
	},
};

/**
 * Inline code within flowing text content.
 */
export const InlineCodeInText: Story = {
	args: {
		children: [
			"When working with React, you'll frequently use hooks like `useState` for local state management. The `useEffect` hook handles side effects, while `useContext` provides access to context values.",
			"",
			"For API calls, prefer `useQuery` from React Query over manual `fetch` calls in `useEffect`. This approach provides better caching, loading states, and error handling.",
			"",
			"**Quick Reference:** `const [state, setState] = useState(initialValue)`",
		].join("\n"),
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl prose prose-zinc dark:prose-invert">
				<Story />
			</div>
		),
	],
};

/**
 * Comprehensive AI mentor response showcasing all markdown features.
 */
export const CompleteAIResponse: Story = {
	args: {
		children: [
			"## Great question about React Query! 🚀",
			"",
			"Here's a comprehensive guide to **data fetching** in modern React applications.",
			"",
			"### Why React Query?",
			"",
			"~~Traditional useEffect data fetching~~ has several issues:",
			"",
			"- [ ] Manual loading states",
			"- [ ] No caching mechanism",
			"- [ ] Complex error handling",
			"- [x] **React Query solves all these!**",
			"",
			"### Basic Usage",
			"",
			"```typescript",
			"import { useQuery } from '@tanstack/react-query';",
			"",
			"function UserProfile({ userId }: { userId: string }) {",
			"  const { data, isLoading, error } = useQuery({",
			"    queryKey: ['user', userId],",
			"    queryFn: () => fetchUser(userId),",
			"    staleTime: 5 * 60 * 1000, // 5 minutes",
			"  });",
			"",
			"  if (isLoading) return <div>Loading...</div>;",
			"  if (error) return <div>Error: {error.message}</div>;",
			"  ",
			"  return <div>Welcome, {data?.name}!</div>;",
			"}",
			"```",
			"",
			"### Feature Comparison",
			"",
			"| Feature | useEffect | React Query | Notes |",
			"|---------|-----------|-------------|-------|",
			"| Caching | ❌ Manual | ✅ Automatic | RQ handles cache invalidation |",
			"| Loading States | ❌ Manual | ✅ Built-in | `isLoading`, `isFetching` etc. |",
			"| Error Handling | ❌ Complex | ✅ Simple | Built-in error boundaries |",
			"| Background Updates | ❌ None | ✅ Smart | Refetch on window focus |",
			"",
			"### Advanced Patterns[^1]",
			"",
			"**Mutations with optimistic updates:**",
			"",
			"```typescript",
			"const updateUser = useMutation({",
			"  mutationFn: updateUserApi,",
			"  onMutate: async (newUser) => {",
			"    // Optimistically update the UI",
			"    await queryClient.cancelQueries(['user', userId]);",
			"    const previousUser = queryClient.getQueryData(['user', userId]);",
			"    queryClient.setQueryData(['user', userId], newUser);",
			"    return { previousUser };",
			"  },",
			"  onError: (err, newUser, context) => {",
			"    // Rollback on error",
			"    queryClient.setQueryData(['user', userId], context?.previousUser);",
			"  },",
			"});",
			"```",
			"",
			"### Pro Tips 💡",
			"",
			"1. **Always use query keys** for proper caching",
			"2. **Set appropriate stale times** based on data freshness needs",
			"3. **Handle loading and error states** for better UX",
			"4. **Use mutations for server state changes**",
			"",
			"For more advanced techniques, check the [React Query docs](https://tanstack.com/query/latest/docs/react/overview).",
			"",
			"**Remember:** React Query is not just a data fetching library—it's a **server state management** solution!",
			"",
			"[^1]: Advanced patterns require understanding React Query's internal mechanics and should be used judiciously in production applications.",
		].join("\n"),
	},
	parameters: {
		docs: {
			description: {
				story:
					"Comprehensive AI mentor response demonstrating all markdown and GFM features.",
			},
		},
	},
};

/**
 * Empty content edge case.
 */
export const EmptyContent: Story = {
	args: {
		children: "",
	},
	parameters: {
		docs: {
			description: {
				story: "Edge case showing how the component handles empty content.",
			},
		},
	},
};

/**
 * Minimal content with just plain text.
 */
export const PlainText: Story = {
	args: {
		children: "Just a simple line of text without any markdown formatting.",
	},
};
