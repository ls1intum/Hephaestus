import { argsToTemplate, moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ButtonDirective, args, argTypes } from './button.component';
import { LucideAngularModule, ChevronRight, Mail, Loader2 } from 'lucide-angular';
import { fn } from '@storybook/test';

type CustomArgs = {
  disabled: boolean;
  onClick: () => void;
};

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<CustomArgs> = {
  title: 'UI/Button',
  component: ButtonDirective,
  tags: ['autodocs'],
  args: {
    ...args,
    disabled: false,
    onClick: fn()
  },
  argTypes: {
    ...argTypes,
    disabled: {
      control: 'boolean'
    },
    onClick: {
      action: 'onClick'
    }
  }
};

export default meta;
type Story = StoryObj<ButtonDirective>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Primary: Story = {
  render: (args) => ({
    props: args,
    template: `<button appButton ${argsToTemplate(args)}>Primary</button>`
  })
};

export const Secondary: Story = {
  args: {
    variant: 'secondary',
    size: 'default'
  },

  render: (args) => ({
    props: args,
    template: `<button appButton ${argsToTemplate(args)}>Secondary</button>`
  })
};

export const Destructive: Story = {
  args: {
    variant: 'destructive',
    size: 'default'
  },

  render: (args) => ({
    props: args,
    template: `<button appButton ${argsToTemplate(args)}>Destructive</button>`
  })
};

export const Outline: Story = {
  args: {
    variant: 'outline',
    size: 'default'
  },

  render: (args) => ({
    props: args,
    template: `<button appButton ${argsToTemplate(args)}>Outline</button>`
  })
};

export const Ghost: Story = {
  args: {
    variant: 'ghost',
    size: 'default'
  },

  render: (args) => ({
    props: args,
    template: `<button appButton ${argsToTemplate(args)}>Ghost</button>`
  })
};

export const Link: Story = {
  args: {
    variant: 'link',
    size: 'default'
  },

  render: (args) => ({
    props: args,
    template: `<button appButton ${argsToTemplate(args)}>Link</button>`
  })
};

export const Icon: Story = {
  decorators: [
    moduleMetadata({
      imports: [LucideAngularModule.pick({ ChevronRight })]
    })
  ],
  render: (args) => ({
    props: {
      variant: 'outline',
      size: 'icon'
    },
    template: `<button appButton ${argsToTemplate(args)}><lucide-icon name="chevron-right" class="size-4"/></button>`
  })
};

export const WithIcon: Story = {
  decorators: [
    moduleMetadata({
      imports: [LucideAngularModule.pick({ Mail })]
    })
  ],
  render: (args) => ({
    props: {
      variant: 'default',
      size: 'default'
    },
    template: `<button appButton ${argsToTemplate(args)}><lucide-icon name="mail" class="mr-2 size-4"/>Login with Email</button>`
  })
};

export const Loading: Story = {
  decorators: [
    moduleMetadata({
      imports: [LucideAngularModule.pick({ Loader2 })]
    })
  ],
  render: (args) => ({
    props: {
      variant: 'default',
      size: 'default',
      disabled: true
    },
    template: `<button appButton ${argsToTemplate(args)}><lucide-icon name="loader-2" class="mr-2 size-4 animate-spin"/>Please wait</button>`
  })
};
