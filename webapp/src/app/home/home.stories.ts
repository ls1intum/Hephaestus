import { type Meta, type StoryObj } from '@storybook/angular';
import { HomeComponent } from './home.component';

const meta: Meta<HomeComponent> = {
  title: 'Pages/Home',
  component: HomeComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<HomeComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-home />`
  })
};
