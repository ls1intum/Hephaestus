import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { InputComponent } from './input.component';

const meta: Meta<InputComponent> = {
  component: InputComponent,
  tags: ['autodocs'],
  args: {
    messageText: ''
  }
};

export default meta;
type Story = StoryObj<InputComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-chat-input ${argsToTemplate(args)} />`
  })
};
