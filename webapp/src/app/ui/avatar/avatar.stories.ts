import { argsToTemplate, moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { AvatarComponent } from './avatar.component';
import { AvatarImageComponent } from './avatar-image.component';
import { AvatarFallbackComponent } from './avatar-fallback.component';

type CustomArgs = {
  src: string;
  alt: string;
  delayMs: number;
};

const meta: Meta<CustomArgs> = {
  title: 'UI/Avatar',
  component: AvatarComponent,
  decorators: [
    moduleMetadata({
      imports: [AvatarImageComponent, AvatarFallbackComponent]
    })
  ],
  tags: ['autodocs'],
  args: {
    src: 'https://github.com/shadcn.png',
    alt: '@shadcn',
    delayMs: 600
  },
  argTypes: {
    src: {
      control: {
        type: 'text'
      }
    },
    alt: {
      control: {
        type: 'text'
      }
    },
    delayMs: {
      control: {
        type: 'number'
      }
    }
  }
};

export default meta;
type Story = StoryObj<AvatarComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `
      <app-avatar>
        <app-avatar-image ${argsToTemplate(args)}/>
        <app-avatar-fallback ${argsToTemplate(args)}>CN</app-avatar-fallback>
      </app-avatar>
    `
  })
};
