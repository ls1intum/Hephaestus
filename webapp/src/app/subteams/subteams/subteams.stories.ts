import { type Meta, type StoryObj } from '@storybook/angular';
import { SubteamsComponent } from './subteams.component';

const meta: Meta<SubteamsComponent> = {
  component: SubteamsComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<SubteamsComponent>;

export const Default: Story = {
  /*
  args: {
    teamsQuery: {
      data: [
        {
          id: 1,
          name: 'Team 1',
          color: 'red',
          repositories: [],
          labels: [],
          members: [
            {
              id: 1,
              login: 'user1',
              avatarUrl: '',
              name: 'User 1',
              htmlUrl: ''
            }]
        },
        {
          id: 2,
          name: 'Team 2',
          color: 'blue',
          repositories: [],
          labels: [],
          members: [
            {
              id: 2,
              login: 'user2',
              avatarUrl: '',
              name: 'User 2',
              htmlUrl: ''
            }
          ]
        }
      ]
    }
  }*/
};
