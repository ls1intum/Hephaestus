import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { UserProfileComponent } from './user-profile.component';
import { ActivatedRoute } from '@angular/router';

const meta: Meta<UserProfileComponent> = {
  title: 'pages/user',
  component: UserProfileComponent,
  tags: ['autodocs'],
  decorators: [
    moduleMetadata({
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => 'krusche'
              }
            }
          }
        }
      ]
    })
  ]
};

export default meta;
type Story = StoryObj<UserProfileComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-user-profile />`
  })
};
