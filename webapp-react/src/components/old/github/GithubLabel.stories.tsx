import type { Meta, StoryObj } from '@storybook/react';
import { GithubLabel } from './GithubLabel';

const meta: Meta<typeof GithubLabel> = {
  title: 'Components/GitHub/GithubLabel',
  component: GithubLabel,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  }
};

export default meta;
type Story = StoryObj<typeof GithubLabel>;

export const Bug: Story = {
  args: {
    label: {
      name: 'bug',
      color: 'd73a4a',
      description: 'Something isn\'t working'
    }
  }
};

export const Enhancement: Story = {
  args: {
    label: {
      name: 'enhancement',
      color: 'a2eeef',
      description: 'New feature or request'
    }
  }
};

export const Documentation: Story = {
  args: {
    label: {
      name: 'documentation',
      color: '0075ca',
      description: 'Improvements or additions to documentation'
    }
  }
};

export const GoodFirstIssue: Story = {
  args: {
    label: {
      name: 'good first issue',
      color: '7057ff',
      description: 'Good for newcomers'
    }
  }
};

export const Help: Story = {
  args: {
    label: {
      name: 'help wanted',
      color: '008672',
      description: 'Extra attention is needed'
    }
  }
};

export const NoDescription: Story = {
  args: {
    label: {
      name: 'priority: high',
      color: 'ff0000'
    }
  }
};