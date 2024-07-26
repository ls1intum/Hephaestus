import type { Meta, StoryObj } from '@storybook/angular';
import { toArgs } from '@app/storybook.helper';
import { CounterComponent } from './counter.component';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<CounterComponent> = {
  title: 'Example/Counter',
  component: CounterComponent,
  tags: ['autodocs'],
  argTypes: toArgs<CounterComponent>({
    title: "test",
    byCount: 2,
  }),
};

export default meta;
type Story = StoryObj<CounterComponent>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Primary: Story = {
  args: {
    title: 'Counter',
    byCount: 2
  },
};