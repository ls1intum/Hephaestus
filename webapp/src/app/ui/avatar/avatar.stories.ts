import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { AppAvatarComponent, args, argTypes } from './avatar.component';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<AppAvatarComponent> = {
  title: 'UI/Avatar',
  component: AppAvatarComponent,
  tags: ['autodocs'],
  args: {
    ...args,
  },
  argTypes: {
    ...argTypes,
  },
};

export default meta;
type Story = StoryObj<AppAvatarComponent>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Default: Story = {
  args: {
    variant: 'medium',
    src: 'https://i.pravatar.cc/64?img=1',
    alt: 'avatar',
    class: '',
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`,
  }),
};

export const Small: Story = {
  args: {
    variant: 'small',
    src: 'https://placehold.co/24',
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`,
  }),
};

export const Medium: Story = {
  args: {
    variant: 'medium',
    src: 'https://placehold.co/40',
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}>MD</app-avatar>`,
  }),
};

export const Large: Story = {
  args: {
    variant: 'large',
    src: 'https://placehold.co/56',
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}>LG</app-avatar>`,
  }),
};

export const WithImage: Story = {
  args: {
    variant: 'medium',
    src: 'https://i.pravatar.cc/40',
  },

  render: (args) => ({
    props: {
      variant: 'outline',
      size: 'icon',
    },
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`,
  }),
};
