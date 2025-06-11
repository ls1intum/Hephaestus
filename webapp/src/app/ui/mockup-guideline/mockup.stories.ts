import { type Meta, type StoryObj } from '@storybook/angular';
import { Message } from '@app/core/modules/openapi';
import { MockupGuidelineComponent } from '@app/ui/mockup-guideline/mockup-guideline.component';

const meta: Meta<MockupGuidelineComponent> = {
  component: MockupGuidelineComponent,
  tags: ['autodocs'],
  args: {}
};

export default meta;
type Story = StoryObj<MockupGuidelineComponent>;

export const Default: Story = {};
