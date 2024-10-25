import { type Meta, type StoryObj } from '@storybook/angular';
import { AboutComponent } from './about.component';

const meta: Meta<AboutComponent> = {
  title: 'pages/about',
  component: AboutComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<AboutComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-about />`
  })
};
