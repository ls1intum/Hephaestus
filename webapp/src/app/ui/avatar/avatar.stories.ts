import { argsToTemplate, moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { AvatarComponent } from './avatar.component';
import { AvatarImageComponent } from './avatar-image.component';
import { AvatarFallbackComponent } from './avatar-fallback.component';

const meta: Meta<AvatarComponent> = {
  title: 'UI/Avatar',
  component: AvatarComponent,
  subcomponents: {
    AvatarImageComponent,
    AvatarFallbackComponent
  },
  decorators: [
    moduleMetadata({
      imports: [AvatarImageComponent, AvatarFallbackComponent]
    })
  ],
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<AvatarComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `
      <app-avatar>
        <app-avatar-image src="https://github.com/shadcn.png" alt="@shadcn" />
        <app-avatar-fallback>CN</app-avatar-fallback>
      </app-avatar>
    `
  })
};
