import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { AppButtonComponent, args, argTypes } from './button.component';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<AppButtonComponent> = {
  title: 'Example/Button2',
  component: AppButtonComponent,
  tags: ['autodocs'],
  args,
  argTypes,
};

export default meta;
type Story = StoryObj<AppButtonComponent>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Primary: Story = {
  render: (args) => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Click me</app-button>`,
  })
};