import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { TableComponent } from './table.component';
import { Leaderboard } from 'app/@types/leaderboard';

const meta: Meta<TableComponent> = {
  title: 'UI/Table',
  component: TableComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<TableComponent>;

const defaultData: Leaderboard.Entry[] = [
  { githubName: 'shadcn', name: 'I', score: 90, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'A', score: 10, total: 100, changes_requested: 1, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'B', score: 20, total: 100, changes_requested: 0, approvals: 1, comments: 0 },
  { githubName: 'shadcn', name: 'C', score: 30, total: 100, changes_requested: 0, approvals: 0, comments: 1 },
  { githubName: 'shadcn', name: 'D', score: 40, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'E', score: 50, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'F', score: 60, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'G', score: 70, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'H', score: 80, total: 100, changes_requested: 0, approvals: 0, comments: 0 }
];

export const Default: Story = {
  args: {
    data: defaultData
  },

  render: (args) => ({
    props: args,
    template: `<app-table ${argsToTemplate(args)}></app-table>`
  })
};
