import { type Meta, type StoryObj } from '@storybook/angular';
import { AdminComponent } from './admin.component';

const meta: Meta<AdminComponent> = {
  title: 'pages/admin',
  component: AdminComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<AdminComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-admin />`
  })
};
