import { Meta, StoryObj } from '@storybook/angular';
import { MetricsViewComponent } from './metrics-view.component';

const meta: Meta<MetricsViewComponent> = {
  title: 'Analytics/MetricsView',
  component: MetricsViewComponent,
  tags: ['autodocs'],
  decorators: [
    // Add any necessary decorators
  ]
};

export default meta;
type Story = StoryObj<MetricsViewComponent>;

export const Default: Story = {};
