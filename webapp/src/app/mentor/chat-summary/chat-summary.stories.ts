import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { ChatSummaryComponent } from './chat-summary.component';

const meta: Meta<ChatSummaryComponent> = {
  component: ChatSummaryComponent,
  tags: ['autodocs'],
  args: {
    status: ['AI Mentor MVP', 'Sentry for Spring Boot & Angular', 'In review: Elo-based league system'],
    impediments: ['Our API occasionally returns 504 Gateway Timeouts - new solutions are being actively investigated test tube Conducting a Hephaestus user experience survey'],
    promises: ['Conducting a Hephaestus user experience survey']
  }
};

export default meta;
type Story = StoryObj<ChatSummaryComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,

    template: `<app-chat-summary ${argsToTemplate(args)} />`
  })
};
