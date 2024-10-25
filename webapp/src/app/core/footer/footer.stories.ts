import { type Meta, type StoryObj } from '@storybook/angular';
import { FooterComponent } from './footer.component';

const meta: Meta<FooterComponent> = {
  component: FooterComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<FooterComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-footer />`
  })
};
