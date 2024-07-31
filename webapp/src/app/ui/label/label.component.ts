import { Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { VariantProps } from 'class-variance-authority';
import { cn } from 'app/utils';
import { cva } from 'app/storybook.helper';

const [labelVariants, args, argTypes] = cva(
  'text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70',
  {
    variants: {
      size: {
        default: 'text-base',
        sm: 'text-sm',
        lg: 'text-lg',
      },
    },
    defaultVariants: {
      size: 'default',
    },
  },
);

export { args, argTypes };

interface LabelVariants extends VariantProps<typeof labelVariants> {}

@Component({
  selector: 'app-label',
  standalone: true,
  templateUrl: './label.component.html',
})
export class AppLabelComponent {
  class = input<ClassValue>('');
  for = input<string>('');
  size = input<LabelVariants['size']>('default');

  computedClass = computed(() =>
    cn(labelVariants({ size: this.size() }), this.class()),
  );
}
