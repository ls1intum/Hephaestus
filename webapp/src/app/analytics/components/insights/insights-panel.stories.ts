import { Meta, StoryObj } from '@storybook/angular';
import { InsightsPanelComponent } from './insights-panel.component';

const meta: Meta<InsightsPanelComponent> = {
  title: 'Analytics/InsightsPanel',
  component: InsightsPanelComponent,
  tags: ['autodocs'],
  decorators: [
    // Add any necessary decorators
  ]
};

export default meta;
type Story = StoryObj<InsightsPanelComponent>;

export const Default: Story = {};
