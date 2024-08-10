import { Component, computed, input, output } from '@angular/core';
import type { ClassValue } from 'clsx';
import { VariantProps } from 'class-variance-authority';
import { cn } from 'app/utils';
import { cva } from 'app/storybook.helper';

const [inputVariants, args, argTypes] = cva(
  'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
  {
    variants: {
      size: {
        default: 'h-10 px-4 py-2',
        sm: 'h-9 px-2 py-1',
        lg: 'h-11 px-4 py-3'
      }
    },
    defaultVariants: {
      size: 'default'
    }
  }
);

export { args, argTypes };

interface InputVariants extends VariantProps<typeof inputVariants> {}

@Component({
  selector: 'app-input',
  standalone: true,
  templateUrl: './input.component.html'
})
export class InputComponent {
  class = input<ClassValue>('');
  type = input<string>('text');
  placeholder = input<string>('');
  size = input<InputVariants['size']>('default');
  disabled = input<boolean>(false);
  value = input<string>('');
  id = input<string>('');

  valueChange = output<string>();

  onInput(event: Event) {
    const inputValue = (event.target as HTMLInputElement).value;
    this.valueChange.emit(inputValue);
  }

  computedClass = computed(() => cn(inputVariants({ size: this.size() }), this.class()));
}
