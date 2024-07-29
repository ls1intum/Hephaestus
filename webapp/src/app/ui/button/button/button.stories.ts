import { argsToTemplate, moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { AppButtonComponent, args, argTypes } from './button.component';
import { LucideAngularModule, ChevronRight, Mail, Loader2 } from 'lucide-angular';
import { within, userEvent, expect, fn } from '@storybook/test';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<AppButtonComponent> = {
  title: 'UI/Button',
  component: AppButtonComponent,
  tags: ['autodocs'],
  args: {
    ...args,
    disabled: false,
    onClick: fn(),
  },
  argTypes: {
    ...argTypes,
    disabled: {
      control: 'boolean',
    },
  },
};

export default meta;
type Story = StoryObj<AppButtonComponent>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Primary: Story = {
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByText('Primary'));
    await expect(args.onClick).toHaveBeenCalled();
  },
  render: (args) => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Primary</app-button>`,
  })
};

export const Secondary: Story = {
  args: {
    variant: "secondary",
    size: "default"
  },

  render: args => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Secondary</app-button>`
  })
};

export const Destructive: Story = {
  args: {
    variant: "destructive",
    size: "default"
  },

  render: args => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Destructive</app-button>`
  })
};

export const Outline: Story = {
  args: {
    variant: "outline",
    size: "default"
  },

  render: args => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Outline</app-button>`
  })
};

export const Ghost: Story = {
  args: {
    variant: "ghost",
    size: "default"
  },

  render: args => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Ghost</app-button>`
  })
};

export const Link: Story = {
  args: {
    variant: "link",
    size: "default"
  },

  render: args => ({
    props: args,
    template: `<app-button ${argsToTemplate(args)}>Link</app-button>`
  })
};

export const Icon: Story = {
  decorators: [
    moduleMetadata({
      imports: [LucideAngularModule.pick({ ChevronRight })],
    }),
  ],
  render: (args) => ({
    props: {
      variant: 'outline',
      size: 'icon',
    },
    template: `<app-button ${argsToTemplate(args)}><lucide-icon name="chevron-right" class="size-4"/></app-button>`,
  })
};

export const WithIcon: Story = {
  decorators: [
    moduleMetadata({
      imports: [LucideAngularModule.pick({ Mail })],
    }),
  ],
  render: (args) => ({
    props: {
      variant: 'default',
      size: 'default',
    },
    template: `<app-button ${argsToTemplate(args)}><lucide-icon name="mail" class="mr-2 size-4"/>Login with Email</app-button>`,
  })
};

export const Loading: Story = {
  decorators: [
    moduleMetadata({
      imports: [LucideAngularModule.pick({ Loader2 })],
    }),
  ],
  render: (args) => ({
    props: {
      variant: 'default',
      size: 'default',
      disabled: true,
    },
    template: `<app-button ${argsToTemplate(args)}><lucide-icon name="loader-2" class="mr-2 size-4 animate-spin"/>Please wait</app-button>`,
  })
};
