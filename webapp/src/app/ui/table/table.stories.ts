import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { TableComponent } from './table.component';

const meta: Meta<TableComponent> = {
  title: 'UI/Table',
  component: TableComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<TableComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-table ${argsToTemplate(args)}></app-table>`
  })
};
