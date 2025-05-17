import type { Meta, StoryObj } from '@storybook/react';
import { 
  LeagueBronzeIcon, 
  LeagueNoneIcon, 
  LeagueSilverIcon, 
  LeagueGoldIcon, 
  LeagueDiamondIcon, 
  LeagueMasterIcon 
} from './LeagueIcons';

const meta: Meta<typeof LeagueBronzeIcon> = {
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  args: {
    size: 'default',
  },
  argTypes: {
    size: {
      control: 'inline-radio',
      options: ['sm', 'default', 'lg', 'max', 'full'],
      description: 'Size of the league icon',
    },
  }
};

export default meta;

export const Bronze: StoryObj<typeof LeagueBronzeIcon> = {
  render: ({size}) => <LeagueBronzeIcon size={size} />,
};

export const Silver: StoryObj<typeof LeagueSilverIcon> = {
  render: ({size}) => <LeagueSilverIcon size={size} />,
};

export const Gold: StoryObj<typeof LeagueGoldIcon> = {
  render: ({size}) => <LeagueGoldIcon size={size} />,
};

export const Diamond: StoryObj<typeof LeagueDiamondIcon> = {
  render: ({size}) => <LeagueDiamondIcon size={size} />,
};

export const Master: StoryObj<typeof LeagueMasterIcon> = {
  render: ({size}) => <LeagueMasterIcon size={size} />,
};

export const None: StoryObj<typeof LeagueNoneIcon> = {
  render: ({size}) => <LeagueNoneIcon size={size} />,
};

export const Sizes: StoryObj = {
  render: () => (
    <div className="flex gap-8">
      <div>
        <LeagueGoldIcon size="sm" />
        <div className="mt-2 text-xs text-center text-muted-foreground">Small</div>
      </div>
      <div>
        <LeagueGoldIcon size="default" />
        <div className="mt-2 text-xs text-center text-muted-foreground">Default</div>
      </div>
      <div>
        <LeagueGoldIcon size="lg" />
        <div className="mt-2 text-xs text-center text-muted-foreground">Large</div>
      </div>
      <div>
        <LeagueGoldIcon size="max" />
        <div className="mt-2 text-xs text-center text-muted-foreground">Max</div>
      </div>
      <div className="w-76">
        <LeagueGoldIcon size="full" />
        <div className="mt-2 text-xs text-center text-muted-foreground">Full</div>
      </div>
    </div>
  ),
};