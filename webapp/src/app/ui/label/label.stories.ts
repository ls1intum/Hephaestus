import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LabelComponent, args, argTypes } from './label.component';

const meta: Meta<LabelComponent> = {
  title: 'UI/Label',
  component: LabelComponent,
  tags: ['autodocs'],
  args: {
    ...args,
    for: 'example-input'
  },
  argTypes: {
    ...argTypes
  }
};

export default meta;
type Story = StoryObj<LabelComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-label ${argsToTemplate(args)}>Label</app-label>`
  })
};
