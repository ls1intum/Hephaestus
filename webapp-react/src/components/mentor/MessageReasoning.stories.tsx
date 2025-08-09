import type { Meta, StoryObj } from "@storybook/react";
import { MessageReasoning } from "./MessageReasoning";

/**
 * MessageReasoning component displays AI reasoning with expandable/collapsible content.
 * Shows loading state during reasoning and allows users to toggle visibility of the reasoning details.
 */
const meta = {
	component: MessageReasoning,
	tags: ["autodocs"],
	argTypes: {
		isLoading: {
			description: "Whether the reasoning is currently being generated",
			control: "boolean",
		},
		reasoning: {
			description: "The reasoning content in markdown format",
			control: "text",
		},
	},
	args: {
		isLoading: false,
		reasoning: `I need to analyze this problem step by step:

## Problem Analysis
The user is asking about React component design patterns. This requires understanding:

1. **Component composition** - How to structure components for reusability
2. **State management** - Where and how to manage component state  
3. **Props design** - Creating clean, intuitive component APIs

## Approach
I'll recommend following these principles:
- Keep components focused and single-purpose
- Use composition over inheritance
- Design clear prop interfaces
- Implement proper error boundaries

This approach will result in maintainable, testable components.`,
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl w-full">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof MessageReasoning>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default reasoning state showing completed reasoning that can be toggled.
 */
export const Default: Story = {};

/**
 * Loading state while AI is actively reasoning.
 */
export const Loading: Story = {
	args: {
		isLoading: true,
		reasoning: "", // No content while loading
	},
};

/**
 * Short reasoning example with minimal content.
 */
export const ShortReasoning: Story = {
	args: {
		reasoning:
			"Quick analysis: This is a straightforward question about JavaScript fundamentals. The user needs a clear explanation of closures and their practical applications.",
	},
};

/**
 * Complex reasoning with detailed analysis, code examples, and structured content.
 */
export const ComplexReasoning: Story = {
	args: {
		reasoning: `# Comprehensive Analysis

This question requires a multi-faceted approach to provide a complete answer.

## Technical Context
The user is asking about TypeScript generics, which involves:

### Core Concepts
1. **Type Parameters** - How to define flexible type constraints
2. **Generic Functions** - Creating reusable function signatures
3. **Generic Classes** - Building type-safe class hierarchies
4. **Conditional Types** - Advanced type manipulation

### Code Example
\`\`\`typescript
interface Repository<T> {
  findById(id: string): Promise<T | null>;
  save(entity: T): Promise<T>;
  delete(id: string): Promise<void>;
}

class UserRepository implements Repository<User> {
  async findById(id: string): Promise<User | null> {
    // Implementation details
    return await db.users.findUnique({ where: { id } });
  }
}
\`\`\`

## Best Practices
- Use meaningful constraint names
- Provide default type parameters when appropriate
- Document generic constraints clearly
- Consider variance and type compatibility

## Common Pitfalls
- Over-constraining type parameters
- Not providing sufficient type information
- Mixing runtime and compile-time concerns

This comprehensive approach ensures the user understands both theory and practical application.`,
	},
};

/**
 * Reasoning with lists, formatting, and structured content.
 */
export const StructuredReasoning: Story = {
	args: {
		reasoning: `## Problem Breakdown

The user's question about React hooks requires addressing several key areas:

### 1. Hook Rules
- Only call hooks at the top level
- Only call hooks from React functions
- Use ESLint plugin for enforcement

### 2. Common Patterns
- **useState**: Local component state
- **useEffect**: Side effects and lifecycle
- **useContext**: Consuming context values
- **useCallback**: Memoizing functions
- **useMemo**: Memoizing expensive calculations

### 3. Custom Hooks
Benefits of creating custom hooks:
- Reusable stateful logic
- Better separation of concerns
- Easier testing
- Cleaner component code

### 4. Performance Considerations
> Important: Not all hooks need optimization. Profile first, optimize second.

The recommendation should focus on practical examples and common use cases.`,
	},
};

/**
 * Mathematical reasoning with formulas and calculations.
 */
export const MathematicalReasoning: Story = {
	args: {
		reasoning: `## Mathematical Approach

To solve this algorithm problem, I need to consider the computational complexity:

### Time Complexity Analysis
- **Brute Force**: O(n²) - checking every pair
- **Hash Map**: O(n) - single pass with lookup
- **Sorted Array**: O(n log n) - due to sorting step

### Space Complexity
- Hash map approach uses O(n) additional space
- Two-pointer technique uses O(1) space

### Optimal Solution
Given the constraints (n ≤ 10⁴), the hash map approach is most efficient:

1. **Initialize** empty hash map
2. **Iterate** through array once
3. **Check** if complement exists in map
4. **Return** indices when found

Expected time: O(n), worst case: O(n)

This balances readability with performance for the given constraints.`,
	},
};

/**
 * Empty reasoning content (edge case).
 */
export const EmptyReasoning: Story = {
	args: {
		reasoning: "",
	},
};

/**
 * Reasoning with code blocks and technical details.
 */
export const TechnicalReasoning: Story = {
	args: {
		reasoning: `## Database Query Optimization

The user's performance issue requires analyzing the SQL query execution plan.

### Current Query Issues
\`\`\`sql
SELECT * FROM users u
JOIN orders o ON u.id = o.user_id
WHERE u.created_at > '2024-01-01'
ORDER BY o.created_at DESC;
\`\`\`

**Problems identified:**
- Missing index on \`users.created_at\`
- SELECT * pulls unnecessary columns
- No limit clause for pagination

### Optimized Solution
\`\`\`sql
SELECT u.id, u.name, o.id as order_id, o.total
FROM users u
JOIN orders o ON u.id = o.user_id
WHERE u.created_at > '2024-01-01'
  AND u.status = 'active'
ORDER BY o.created_at DESC
LIMIT 50 OFFSET 0;
\`\`\`

### Required Indexes
\`\`\`sql
CREATE INDEX idx_users_created_status ON users(created_at, status);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at);
\`\`\`

This should reduce query time from ~2000ms to ~50ms.`,
	},
};

/**
 * Shows "Reasoned for a while" when no timing information is available.
 * This simulates loading a message from the database without timing data.
 */
export const ReasonedForAWhile: Story = {
	args: {
		reasoning: `Simple response: The answer is 42.

This was a quick calculation that didn't require extensive reasoning.`,
	},
	parameters: {
		docs: {
			description: {
				story: 'When timing information is not available (e.g., loading from database), the component shows "Reasoned for a while" instead of trying to calculate duration.',
			},
		},
	},
};
