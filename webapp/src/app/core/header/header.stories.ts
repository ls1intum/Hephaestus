import { type Meta, type StoryObj } from '@storybook/angular';
import { HeaderComponent } from './header.component';

const meta: Meta<HeaderComponent> = {
  component: HeaderComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<HeaderComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-header />`
  })
};
