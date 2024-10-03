import { argsToTemplate, moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { action } from '@storybook/addon-actions';
import { LabelComponent } from '@app/ui/label/label.component';
import { InputComponent, args, argTypes } from './input.component';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

const meta: Meta<InputComponent> = {
  title: 'UI/Input',
  component: InputComponent,
  tags: ['autodocs'],
  args: {
    ...args,
    value: '',
    disabled: false,
    size: 'default'
  },
  argTypes: {
    ...argTypes,
    disabled: {
      control: 'boolean'
    },
    onInput: {
      action: 'onInput'
    }
  },
  decorators: [
    moduleMetadata({
      imports: [HlmButtonModule, LabelComponent]
    })
  ]
};

export default meta;
type Story = StoryObj<InputComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-input ${argsToTemplate(args)} placeholder="Enter text here"/>`
  })
};

export const Disabled: Story = {
  args: {
    disabled: true
  },
  render: (args) => ({
    props: args,
    template: `<app-input ${argsToTemplate(args)} placeholder="Disabled input"/>`
  })
};

export const WithLabel: Story = {
  render: (args) => ({
    props: args,
    template: `
      <div class="grid w-full max-w-sm items-center gap-1.5">
        <app-label [for]="input-field" size="sm">Label</app-label>
        <app-input ${argsToTemplate(args)} [id]="input-field" placeholder="Enter text here" class="grow"></app-input>
      </div>
    `
  })
};

export const WithButton: Story = {
  render: (args) => ({
    props: {
      args,
      userInput: '',
      onButtonClick(value: string) {
        action('Button Clicked')(`Input Value: ${value}`);
      }
    },
    template: `
      <div class="flex gap-2 flex-row">
        <app-input ${argsToTemplate(args)} [size]="args.size" [(value)]="userInput" placeholder="Enter text here" class="grow"/>
        <button hlmBtn (onClick)="onButtonClick(userInput)" size="${args.size ?? 'default'}" [disabled]="!userInput">Submit</button>
      </div>
    `
  })
};
