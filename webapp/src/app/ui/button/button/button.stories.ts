import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { toArgs } from '@app/storybook.helper';
import { AppButtonDirective, args, argTypes } from './button.directive';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<AppButtonDirective> = {
  title: 'Example/Button2',
  component: AppButtonDirective,
  tags: ['autodocs'],
  args: toArgs(args),
  argTypes: argTypes,
};

export default meta;
type Story = StoryObj<AppButtonDirective>;

console.log('args', args);
console.log('argTypes', argTypes);

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Primary: Story = {
  render: (args) => ({
    props: args,
    template: `<button appBtn ${argsToTemplate(args)}>Click me</button>`,
  }),
};