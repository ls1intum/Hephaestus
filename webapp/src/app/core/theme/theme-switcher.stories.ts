import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ThemeSwitcherComponent } from './theme-switcher.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories
const meta: Meta<ThemeSwitcherComponent> = {
  title: 'Components/Core/ThemeSwitcher',
  component: ThemeSwitcherComponent,
  tags: ['autodocs'],
  decorators: [
    moduleMetadata({
      imports: [BrowserAnimationsModule]
    })
  ]
};

export default meta;
type Story = StoryObj<ThemeSwitcherComponent>;

// More on writing stories with args: https://storybook.js.org/docs/writing-stories/args
export const Default: Story = {
  render: () => ({
    template: `<app-theme-switcher />`
  })
};
