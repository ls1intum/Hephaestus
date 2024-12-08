import { type Meta, type StoryObj } from '@storybook/angular';
import { LeagueInfoModalComponent } from './info-modal.component';

const meta: Meta<LeagueInfoModalComponent> = {
  component: LeagueInfoModalComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered'
  },
};

export default meta;
type Story = StoryObj<LeagueInfoModalComponent>;

export const Default: Story = {};
