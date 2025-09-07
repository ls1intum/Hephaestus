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
		reasoning: `**Goal**
Build components that are easy to reason about and change.
Minimize implicit behavior and side-effects.
Favor obvious data flow and clear ownership.

**Plan**
Compose small parts with simple contracts.
Keep props minimal but expressive, with sensible defaults.
Introduce error boundaries where the blast radius is smallest.
Document intent alongside edge cases.

**Outcome**
Predictable, testable components that fail gracefully.
Lower cognitive load for future contributors.`,
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
		reasoning: `**Analyzing context**
Collecting signals from prior messages and metadata.
Estimating ambiguity and selecting a suitable approach.`,
	},
};

/**
 * Short reasoning example with minimal content.
 */
export const ShortReasoning: Story = {
	args: {
		reasoning: `**Question**
What is a closure and why is it useful?
Provide a concrete example.

**Answer**
A closure is a function that captures variables from its outer scope.
It enables encapsulation and factories by retaining state across calls.
E.g., a counter function that remembers its internal value.`,
	},
};

/**
 * Complex reasoning with detailed analysis, code examples, and structured content.
 */
export const ComplexReasoning: Story = {
	args: {
		reasoning: `**Context**
TypeScript generics provide flexibility while preserving type safety.
They allow APIs to work across many types without resorting to any.
Good generic design keeps call sites readable.

**Core concepts**
Type parameters with constraints define legal shapes.
Conditional and mapped types model transformations succinctly.
Inference often removes the need to specify T explicitly.

**Example**
\`\`\`ts
function wrap<T>(v: T): { value: T } { return { value: v } }
\`\`\`
This preserves the exact type of v for downstream consumers.

**Best practices**
Prefer fewer, well-named type parameters.
Document constraints and default types.
Avoid over-generalization that harms ergonomics.`,
	},
};

/**
 * Reasoning with lists, formatting, and structured content.
 */
export const StructuredReasoning: Story = {
	args: {
		reasoning: `**Hook rules**
Call hooks at the top level of React functions only.
Never call hooks conditionally; guard inside the effect or callback.
Rely on the ESLint plugin to enforce best practices.

**Common patterns**
useState for local state; useEffect for side-effects.
useMemo and useCallback to memoize expensive work or stable refs.

**Custom hooks**
Extract reusable, stateful logic behind a descriptive API.
Test the hook in isolation to validate behavior.

**Performance**
Measure first; only memoize where it matters.
Prefer algorithmic wins over micro-optimizations.`,
	},
};

/**
 * Mathematical reasoning with formulas and calculations.
 */
export const MathematicalReasoning: Story = {
	args: {
		reasoning: `**Time**
Hash map approach is O(n) due to single pass and O(1) average lookup.
Sorting-based approach is O(n log n) primarily due to the sort.

**Space**
Hash map uses O(n) additional memory for visited elements.
Two-pointer on sorted data can be O(1) extra space.

**Pick**
Given large n and one pass constraints, choose hashing for speed.`,
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
		reasoning: `**Query issues**
SELECT * inflates payload and blocks index-only scans.
Missing composite indexes cause full scans on high-cardinality columns.
No LIMIT hurts p99 latency for large result sets.

**Fix**
Select only required columns; push predicates into WHERE.
Add pagination (LIMIT/OFFSET or keyset) to bound work.

**Indexes**
\`CREATE INDEX idx_users_created_status ON users(created_at, status);\`
\`CREATE INDEX idx_orders_user_created ON orders(user_id, created_at);\`
These enable selective filters and efficient ordering.`,
	},
};

/**
 * Shows "Reasoned for a while" when no timing information is available.
 * This simulates loading a message from the database without timing data.
 */
export const ReasonedForAWhile: Story = {
	args: {
		reasoning: `**Summary**
Answer is 42 based on the problem constraints.
The calculation is straightforward and needs no deeper derivation.`,
	},
	parameters: {
		docs: {
			description: {
				story:
					"When timing isn't available, the header uses the last heading (e.g., 'Summary') instead of a duration.",
			},
		},
	},
};
