import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { SelectComponent } from './select.component';

const meta: Meta<SelectComponent> = {
  title: 'UI/Select',
  component: SelectComponent,
  tags: ['autodocs'],
  args: {
    options: [
      {
        id: 1,
        value: 'option-1',
        label: 'Option 1'
      },
      {
        id: 2,
        value: 'option-2',
        label: 'Option 2'
      },
      {
        id: 3,
        value: 'option-3',
        label: 'Option 3'
      }
    ]
  }
};

export default meta;
type Story = StoryObj<SelectComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-select ${argsToTemplate(args)} />`
  })
};
