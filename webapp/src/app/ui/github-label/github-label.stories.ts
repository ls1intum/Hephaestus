import { argsToTemplate, Meta, StoryObj } from '@storybook/angular';
import { GithubLabelComponent } from './github-label.component';

type FlatArgs = {
  isLoading: boolean;
  name: string;
  color: string;
};

function flatArgsToProps(args: FlatArgs) {
  return {
    isLoading: args.isLoading,
    label: {
      name: args.name,
      color: args.color
    }
  };
}

const meta: Meta<FlatArgs> = {
  component: GithubLabelComponent,
  tags: ['autodocs'],
  args: {
    isLoading: false,
    name: 'bug',
    color: 'f00000'
  },
  argTypes: {
    isLoading: {
      control: {
        type: 'boolean'
      }
    },
    name: {
      control: {
        type: 'text'
      }
    },
    color: {
      control: {
        type: 'text'
      }
    }
  }
};

export default meta;

type Story = StoryObj<GithubLabelComponent>;

export const Default: Story = {
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-github-label ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};

export const isLoading: Story = {
  args: {
    isLoading: true
  },
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-github-label ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};
