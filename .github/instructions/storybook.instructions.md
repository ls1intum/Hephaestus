---
applyTo: "**/*.stories.ts,**/*.stories.tsx"
---
# Storybook Guidelines

## Core Principles

We create stories that **accelerate development** and **improve team communication**. Stories should be practical tools that help developers work faster, not documentation overhead.

## Structure & Setup

- **Co-locate** story files with component files (`Component.stories.tsx`)
- Use `satisfies Meta<typeof Component>` for type safety
- Always include `tags: ["autodocs"]` for automatic documentation generation
- Favor auto-generated titles (omit `title` property)

## Documentation Strategy

### Component-Level Documentation
- Add a **concise JSDoc comment** above the `meta` object explaining the component's purpose and key capabilities
- Focus on **when to use** the component, not implementation details

```typescript
/**
 * CodeBlock component for displaying code snippets and terminal output.
 * Supports both inline code and multi-line code blocks with syntax highlighting.
 */
```

### Story-Level Documentation
- Add **brief JSDoc comments** for each story explaining its specific use case
- Use action-oriented descriptions that help developers choose the right variant

```typescript
/**
 * Multi-line code block for displaying formatted code snippets.
 */
export const CodeBlock: Story = { /* ... */ };
```

## Controls Configuration

### Selective argTypes
- Only configure `argTypes` for props that **benefit from interactive controls**
- Let Storybook infer most controls automatically 
- Add `description` for complex or non-obvious props

```typescript
argTypes: {
  inline: {
    control: 'boolean',
    description: 'Whether to display as inline code or a code block',
  },
  className: {
    description: 'Additional CSS classes for styling',
  },
}
```

### Default Values
- Set meaningful defaults using `args` at the meta level
- Avoid setting `defaultValue` in argTypes (deprecated pattern)

```typescript
const meta = {
  component: CodeBlock,
  args: {
    inline: false,
    className: '',
    children: 'console.log("Hello, world!");',
  },
} satisfies Meta<typeof CodeBlock>;
```

## Story Patterns

### Essential Stories
Every component should have these core stories:
1. **Default** - Standard usage with common props
2. **Variants** - Different visual styles/states
3. **Edge Cases** - Empty states, long content, error states

### Interactive Stories
- Use realistic content that demonstrates actual usage patterns
- Include examples with both short and long content to test layout
- Show different states and configurations

### Content Guidelines
- Use **realistic, relevant content** that reflects actual usage
- Follow our platform's **friendly, supportive engineering voice**
- Use consistent terminology across all components
- Include practical examples developers would actually encounter

## Layout & Presentation

### Parameters
- Use `layout: 'centered'` for isolated components
- Use `layout: 'fullscreen'` for page-level components
- Add appropriate decorators when components need context

```typescript
parameters: {
  layout: 'centered',
  docs: {
    description: {
      component: 'Additional context if the JSDoc comment is insufficient',
    },
  },
},
decorators: [
  (Story) => (
    <div className="max-w-4xl p-6 bg-background">
      <Story />
    </div>
  ),
],
```

## Advanced Patterns

### Composition Stories
Create stories that show components working together:

```typescript
export const InContext: Story = {
  render: () => (
    <div className="prose">
      <p>Here's how to use the API:</p>
      <CodeBlock inline={false} className="">
        {`const response = await fetch('/api/users');
const users = await response.json();`}
      </CodeBlock>
    </div>
  ),
};
```

### Data-Driven Stories
For components with complex requirements, create realistic mock data and scenarios.

## Quality Standards

- **Simplicity over complexity** - Don't over-engineer stories
- **Practical scenarios** - Focus on real use cases over edge cases
- **Visual clarity** - Stories should immediately show component capabilities
- **Performance** - Avoid heavy computations or API calls in stories
- **Maintenance** - Keep stories simple enough that they don't become a burden

## Example Story Structure

```typescript
import type { Meta, StoryObj } from '@storybook/react';
import { CodeBlock } from './CodeBlock';

/**
 * CodeBlock component for displaying code snippets and terminal output.
 * Supports both inline code and multi-line code blocks with proper formatting.
 */
const meta = {
  component: CodeBlock,
  parameters: { layout: 'centered' },
  tags: ['autodocs'],
  argTypes: {
    inline: {
      control: 'boolean',
      description: 'Whether to display as inline code or a code block',
    },
    className: {
      description: 'Additional CSS classes for styling',
    },
  },
  args: {
    inline: false,
    className: '',
  },
} satisfies Meta<typeof CodeBlock>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Multi-line code block for displaying formatted code snippets.
 */
export const Default: Story = {
  args: {
    children: 'console.log("Hello, world!");',
  },
};

/**
 * Inline code for referencing variables or short snippets within text.
 */
export const Inline: Story = {
  args: {
    inline: true,
    children: 'useState',
  },
};
```
