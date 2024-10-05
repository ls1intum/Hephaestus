import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { RequestFeatureComponent } from './request-feature.component';

const meta: Meta<RequestFeatureComponent> = {
  component: RequestFeatureComponent,
  tags: ['autodocs'],
  args: {
    iconOnly: false
  }
};

export default meta;
type Story = StoryObj<RequestFeatureComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-request-feature ${argsToTemplate(args)} />`
  })
};

export const Icon: Story = {
  args: {
    iconOnly: true
  },
  render: (args) => ({
    props: args,
    template: `<app-request-feature ${argsToTemplate(args)} />`
  })
};
