import type { Meta, StoryObj } from '@storybook/react';
import { TeamMemberCard } from './TeamMemberCard';
import type { TeamMember } from './types';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof TeamMemberCard> = {
  title: 'Components/Teams/TeamMemberCard',
  component: TeamMemberCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onRemove: { action: 'member removed' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamMemberCard>;

// Sample team member
const sampleMember: TeamMember = {
  id: 123,
  login: 'johndoe',
  name: 'John Doe',
  avatarUrl: 'https://avatars.githubusercontent.com/u/1234567',
};

export const Default: Story = {
  args: {
    member: sampleMember,
    onRemove: action('member removed'),
    isRemoving: false,
  }
};

export const NoName: Story = {
  args: {
    member: {
      ...sampleMember,
      name: undefined,
    },
    onRemove: action('member removed'),
  }
};

export const LongName: Story = {
  args: {
    member: {
      ...sampleMember,
      name: 'Johnathan Doelington Smithsonian III',
    },
    onRemove: action('member removed'),
  }
};

export const NoRemoveButton: Story = {
  args: {
    member: sampleMember,
  }
};

export const Removing: Story = {
  args: {
    member: sampleMember,
    onRemove: action('member removed'),
    isRemoving: true,
  }
};

export const NoAvatar: Story = {
  args: {
    member: {
      ...sampleMember,
      avatarUrl: undefined,
    },
    onRemove: action('member removed'),
  }
};