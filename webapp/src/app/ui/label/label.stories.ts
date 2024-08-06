import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { AppLabelComponent, args, argTypes } from './label.component';

const meta: Meta<AppLabelComponent> = {
  title: 'UI/Label',
  component: AppLabelComponent,
  tags: ['autodocs'],
  args: {
    ...args,
    for: 'example-input',
  },
  argTypes: {
    ...argTypes,
  },
};

export default meta;
type Story = StoryObj<AppLabelComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-label ${argsToTemplate(args)}>Label</app-label>`,
  }),
};
