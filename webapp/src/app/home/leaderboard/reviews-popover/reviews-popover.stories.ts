import { type Meta, type StoryObj } from '@storybook/angular';
import { ReviewsPopoverComponent } from './reviews-popover.component';

const meta: Meta<ReviewsPopoverComponent> = {
  component: ReviewsPopoverComponent,
  tags: ['autodocs'],
  args: {
    highlight: false,
    reviewedPRs: [
      {
        id: 1,
        repository: {
          name: 'Artemis'
        },
        number: 9231,
        title: 'Fix Artemis',
        htmlUrl: 'https://www.github.com/ls1intum/Artemis/pull/9231'
      },
      {
        id: 2,
        repository: {
          name: 'Hephaestus'
        },
        number: 132,
        title: 'Fix Hephaestus',
        htmlUrl: 'https://www.github.com/ls1intum/Hephaestus/pull/132'
      },
      {
        id: 3,
        repository: {
          name: 'Artemis'
        },
        number: 9232,
        title: 'Fix Artemis',
        htmlUrl: 'https://www.github.com/ls1intum/Artemis/pull/9232'
      }
    ]
  },
  parameters: {
    layout: 'centered'
  }
};

export default meta;
type Story = StoryObj<ReviewsPopoverComponent>;

export const Default: Story = {};

export const ManyPullRequests: Story = {
  args: {
    reviewedPRs: [
      {
        id: 1,
        repository: {
          name: 'Artemis'
        },
        number: 9231,
        title: 'Fix Artemis authentication bug',
        htmlUrl: 'https://www.github.com/ls1intum/Artemis/pull/9231'
      },
      {
        id: 2,
        repository: {
          name: 'Hephaestus'
        },
        number: 132,
        title: 'Improve Hephaestus logging',
        htmlUrl: 'https://www.github.com/ls1intum/Hephaestus/pull/132'
      },
      {
        id: 3,
        repository: {
          name: 'Artemis'
        },
        number: 9232,
        title: 'Add Artemis new feature',
        htmlUrl: 'https://www.github.com/ls1intum/Artemis/pull/9232'
      },
      {
        id: 4,
        repository: {
          name: 'Zeus'
        },
        number: 45,
        title: 'Refactor Zeus module',
        htmlUrl: 'https://www.github.com/ls1intum/Zeus/pull/45'
      },
      {
        id: 5,
        repository: {
          name: 'Athena'
        },
        number: 789,
        title: 'Update Athena dependencies',
        htmlUrl: 'https://www.github.com/ls1intum/Athena/pull/789'
      },
      {
        id: 6,
        repository: {
          name: 'Apollo'
        },
        number: 256,
        title: 'Optimize Apollo queries',
        htmlUrl: 'https://www.github.com/ls1intum/Apollo/pull/256'
      },
      {
        id: 7,
        repository: {
          name: 'Hermes'
        },
        number: 1024,
        title: 'Fix Hermes deployment issue',
        htmlUrl: 'https://www.github.com/ls1intum/Hermes/pull/1024'
      },
      {
        id: 8,
        repository: {
          name: 'Poseidon'
        },
        number: 678,
        title: 'Enhance Poseidon UI',
        htmlUrl: 'https://www.github.com/ls1intum/Poseidon/pull/678'
      },
      {
        id: 9,
        repository: {
          name: 'Hera'
        },
        number: 310,
        title: 'Add Hera analytics feature',
        htmlUrl: 'https://www.github.com/ls1intum/Hera/pull/310'
      },
      {
        id: 10,
        repository: {
          name: 'Demeter'
        },
        number: 555,
        title: 'Refine Demeter data models',
        htmlUrl: 'https://www.github.com/ls1intum/Demeter/pull/555'
      }
    ]
  }
};

export const Empty: Story = {
  args: {
    reviewedPRs: []
  }
};
