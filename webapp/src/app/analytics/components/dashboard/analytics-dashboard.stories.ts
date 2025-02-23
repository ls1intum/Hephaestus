import { Meta, StoryObj } from '@storybook/angular';
import { AnalyticsDashboardComponent } from './analytics-dashboard.component';

const meta: Meta<AnalyticsDashboardComponent> = {
  title: 'Analytics/Dashboard',
  component: AnalyticsDashboardComponent,
  tags: ['autodocs'],
  decorators: [
    // Add any necessary decorators
  ]
};

export default meta;
type Story = StoryObj<AnalyticsDashboardComponent>;

export const Default: Story = {};
