import type { Meta, StoryObj } from '@storybook/react';
import { TeamLabelCard } from './TeamLabelCard';
import type { LabelInfo } from './types';

// Use LabelInfo type instead of TeamLabel
type TeamLabel = LabelInfo;

const meta: Meta<typeof TeamLabelCard> = {
  title: 'Components/Teams/TeamLabelCard',
  component: TeamLabelCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onRemove: { action: 'label removed' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamLabelCard>;

// Sample label data
const sampleLabel: TeamLabel = {
  id: 201,
  name: 'enhancement',
  color: '3b82f6',
  description: 'New feature or request',
  repository: {
    id: 101,
    nameWithOwner: 'organization/frontend'
  }
};

export const Default: Story = {
  args: {
    label: sampleLabel,
    onRemove: undefined,
  }
};

export const WithRemove: Story = {
  args: {
    label: sampleLabel,
    onRemove: (id) => console.log(`Remove label: ${id}`),
  }
};

export const WithRemoveAction: Story = {
  args: {
    label: sampleLabel,
    onRemove: (id) => console.log(`Remove label with ID: ${id}`),
  },
  decorators: [
    (StoryFn) => (
      <div className="w-80">
        <StoryFn />
      </div>
    ),
  ],
};

export const IsRemoving: Story = {
  args: {
    label: sampleLabel,
    onRemove: (id) => console.log(`Remove label with ID: ${id}`),
    isRemoving: true,
  },
  decorators: [
    (StoryFn) => (
      <div className="w-80">
        <StoryFn />
      </div>
    ),
  ],
};

export const NoDescription: Story = {
  args: {
    label: {
      ...sampleLabel,
      description: '',
    },
    onRemove: (id) => console.log(`Remove label with ID: ${id}`),
  },
  decorators: [
    (StoryFn) => (
      <div className="w-80">
        <StoryFn />
      </div>
    ),
  ],
};

export const NoColor: Story = {
  args: {
    label: {
      ...sampleLabel,
      color: '',
    },
    onRemove: (id) => console.log(`Remove label with ID: ${id}`),
  },
  decorators: [
    (StoryFn) => (
      <div className="w-80">
        <StoryFn />
      </div>
    ),
  ],
};

export const MultipleCards: Story = {
  args: {
    label: sampleLabel,
    onRemove: (id) => console.log(`Remove label with ID: ${id}`),
  },
  decorators: [
    () => ( // Remove unused StoryFn parameter
      <div className="w-80 flex flex-col gap-4">
        <TeamLabelCard 
          label={{
            id: 1,
            name: 'bug',
            color: '#d73a4a',
            description: 'Something isn\'t working as expected'
          }}
          onRemove={(id) => console.log(`Remove label with ID: ${id}`)}
        />
        <TeamLabelCard 
          label={{
            id: 2,
            name: 'enhancement',
            color: '#a2eeef',
            description: 'New feature or request'
          }}
          onRemove={(id) => console.log(`Remove label with ID: ${id}`)}
        />
        <TeamLabelCard 
          label={{
            id: 3,
            name: 'documentation',
            color: '#0075ca',
            description: 'Improvements or additions to documentation'
          }}
          onRemove={(id) => console.log(`Remove label with ID: ${id}`)}
        />
      </div>
    ),
  ],
};