import type { Meta, StoryObj } from '@storybook/react';
import { BadPracticeCard, BadPracticeState } from './BadPracticeCard';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof BadPracticeCard> = {
  title: 'Components/Practices/BadPracticeCard',
  component: BadPracticeCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onResolveAsFixed: { action: 'resolved as fixed' },
    onResolveAsWontFix: { action: 'resolved as won\'t fix' },
    onProvideFeedback: { action: 'feedback provided' },
  }
};

export default meta;
type Story = StoryObj<typeof BadPracticeCard>;

export const Open: Story = {
  args: {
    title: 'Missing Error Handling',
    description: 'This function lacks proper error handling for network requests, which could lead to unexpected behavior if the API call fails.',
    state: BadPracticeState.Open,
    id: 1,
    onResolveAsFixed: action('resolved as fixed'),
    onResolveAsWontFix: action('resolved as won\'t fix'),
    onProvideFeedback: action('feedback provided')
  }
};

export const Fixed: Story = {
  args: {
    title: 'Unused Imports',
    description: 'There are several unused imports at the top of this file that should be removed to improve code cleanliness and build size.',
    state: BadPracticeState.Fixed,
    id: 2,
    onResolveAsFixed: action('resolved as fixed'),
    onResolveAsWontFix: action('resolved as won\'t fix'),
    onProvideFeedback: action('feedback provided')
  }
};

export const WontFix: Story = {
  args: {
    title: 'Inconsistent Naming Convention',
    description: 'This component uses a mix of camelCase and snake_case variable names, which is inconsistent with the project\'s style guide.',
    state: BadPracticeState.WontFix,
    id: 3,
    onResolveAsFixed: action('resolved as fixed'),
    onResolveAsWontFix: action('resolved as won\'t fix'),
    onProvideFeedback: action('feedback provided')
  }
};

export const FeedbackProvided: Story = {
  args: {
    title: 'Potential Memory Leak',
    description: 'This component doesn\'t properly clean up resources in useEffect, which might lead to memory leaks when the component unmounts.',
    state: BadPracticeState.Feedback,
    id: 4,
    onResolveAsFixed: action('resolved as fixed'),
    onResolveAsWontFix: action('resolved as won\'t fix'),
    onProvideFeedback: action('feedback provided')
  }
};