import { type Meta, type StoryObj } from '@storybook/angular';
import { SkeletonComponent } from './skeleton.component';

const meta: Meta<SkeletonComponent> = {
  title: 'UI/Skeleton',
  component: SkeletonComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<SkeletonComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-skeleton class="size-12" />`
  })
};
