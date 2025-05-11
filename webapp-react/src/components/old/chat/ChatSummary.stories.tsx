import type { Meta, StoryObj } from '@storybook/react';
import { ChatSummary } from './ChatSummary';

const meta: Meta<typeof ChatSummary> = {
  title: 'Components/Chat/ChatSummary',
  component: ChatSummary,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof ChatSummary>;

export const FullSummary: Story = {
  args: {
    status: [
      {
        title: 'UI Component Library',
        description: 'You\'ve been working on migrating the component library from Angular to React with Storybook integration.'
      },
      {
        title: 'API Integration',
        description: 'You\'ve implemented several API endpoints for user authentication and data retrieval.'
      }
    ],
    impediments: [
      {
        title: 'Test Coverage',
        description: 'The current test coverage is below the target of 80%.'
      },
      {
        title: 'Documentation',
        description: 'Some components are missing proper documentation.'
      }
    ],
    promises: [
      {
        title: 'Documentation Generation',
        description: 'I can help you generate comprehensive documentation for your components.'
      },
      {
        title: 'Test Strategy',
        description: 'I can suggest a testing strategy to improve test coverage.'
      }
    ]
  }
};

export const StatusOnly: Story = {
  args: {
    status: [
      {
        title: 'Code Migration',
        description: 'You\'ve successfully migrated 15 components from Angular to React.'
      },
      {
        title: 'UI Improvements',
        description: 'You\'ve enhanced the user experience with better error handling and loading states.'
      }
    ],
    impediments: [],
    promises: []
  }
};

export const ImpedimentsOnly: Story = {
  args: {
    status: [],
    impediments: [
      {
        title: 'Performance Issues',
        description: 'The application is experiencing slow load times on the dashboard page.'
      },
      {
        title: 'Browser Compatibility',
        description: 'There are rendering issues in Safari that need to be addressed.'
      }
    ],
    promises: []
  }
};

export const PromisesOnly: Story = {
  args: {
    status: [],
    impediments: [],
    promises: [
      {
        title: 'Component Review',
        description: 'I\'ll help review your component architecture for best practices.'
      },
      {
        title: 'Performance Optimization Tips',
        description: 'I can suggest ways to optimize your React app performance.'
      }
    ]
  }
};