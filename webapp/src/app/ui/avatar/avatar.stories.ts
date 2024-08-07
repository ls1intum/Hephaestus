import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { AppAvatarComponent, args, argTypes } from './avatar.component';

const meta: Meta<AppAvatarComponent> = {
  title: 'UI/Avatar',
  component: AppAvatarComponent,
  tags: ['autodocs'],
  args: {
    ...args
  },
  argTypes: {
    ...argTypes
  }
};

export default meta;
type Story = StoryObj<AppAvatarComponent>;

export const Default: Story = {
  args: {
    variant: 'medium',
    src: 'https://i.pravatar.cc/40?img=1',
    alt: 'avatar',
    class: ''
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`
  })
};

export const Small: Story = {
  args: {
    variant: 'small',
    src: 'https://i.pravatar.cc/24?img=1'
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`
  })
};

export const Medium: Story = {
  args: {
    variant: 'medium',
    src: 'https://i.pravatar.cc/40?img=1'
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}>MD</app-avatar>`
  })
};

export const Large: Story = {
  args: {
    variant: 'large',
    src: 'https://i.pravatar.cc/56?img=1',
    alt: 'avatar',
    class: ''
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}>LG</app-avatar>`
  })
};

export const WithRandomImage: Story = {
  args: {
    variant: 'large',
    src: 'https://i.pravatar.cc/56',
    alt: 'avatar'
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`
  })
};

export const WithFallback: Story = {
  args: {
    variant: 'medium',
    src: 'foobar.jpg',
    fallback: 'https://placehold.co/40',
    alt: 'fallback'
  },

  render: (args) => ({
    props: args,
    template: `<app-avatar ${argsToTemplate(args)}></app-avatar>`
  })
};
