import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { AiMentorComponent } from './ai-mentor.component';

const meta: Meta<AiMentorComponent> = {
  component: AiMentorComponent,
  tags: ['autodocs'],
  args: {
    iconOnly: false
  }
};

export default meta;
type Story = StoryObj<AiMentorComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-ai-mentor ${argsToTemplate(args)} />`
  })
};

export const Icon: Story = {
  args: {
    iconOnly: true
  },
  render: (args) => ({
    props: args,
    template: `<app-ai-mentor ${argsToTemplate(args)} />`
  })
};
