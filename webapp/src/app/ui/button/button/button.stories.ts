import type { Meta, StoryObj } from '@storybook/angular';
import { toArgs } from '@app/storybook.helper';
import { ButtonComponent } from './button.component';
import { InputSignal } from '@angular/core';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<ButtonComponent> = {
  title: 'Example/Button2',
  component: ButtonComponent,
  tags: ['autodocs'],
  argTypes: {
    variant: 'default' as unknown as InputSignal<'default' | 'primary' | 'secondary'>,
    size: 'lg' as unknown as InputSignal<'sm' | 'md' | 'lg'>,
  },
};

export default meta;
type Story = StoryObj<ButtonComponent>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Primary: Story = {
  args: {
    variant: 'default',
    size: 'lg',
  },
};