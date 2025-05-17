---
applyTo: "**/*.stories.ts,**/*.stories.tsx"
---
# Storybook Guidelines

## Structure & Basic Setup

- Co-locate story files with component files
- Use `satisfies Meta<typeof Component>` for type safety
- Use `tags: ["autodocs"]` to generate documentation

## Documentation

- Add a JSDoc comment above the `meta` object describing the component's purpose
- Add JSDoc comments for each story variant explaining its use case

```typescript
/**
 * Button component for user interactions with multiple variants and states.
 */
```

## Component Controls

- Document props using `argTypes` with clear descriptions
- Use appropriate controls for each prop type (boolean, select, text, etc.)
- Set default values for common props

```typescript
argTypes: {
  size: { 
    control: 'select',
    options: ['default', 'sm', 'lg'],
    description: 'Button size',
  },
  variant: { 
    control: 'select', 
    description: 'Visual style',
  },
}
```

## Example Story

```typescript
import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { Button } from './Button';

/**
 * Button component for user interactions.
 */
const meta = {
  component: Button,
  parameters: { layout: 'centered' },
  tags: ['autodocs'],
  argTypes: {
    size: { control: 'select', options: ['small', 'large'] },
    backgroundColor: { control: 'color' },
  },
  args: { onClick: fn() },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Primary button for main actions.
 */
export const Primary: Story = {
  args: {
    primary: true,
    label: 'Button',
  },
};

/**
 * Secondary button for less important actions.
 */
export const Secondary: Story = {
  args: {
    label: 'Button',
  },
};
```

## UX Writing

- Use realistic and relevant content in stories
- Follow our platform's friendly, supportive voice
- Keep text clear and concise
- Use consistent terminology across components
