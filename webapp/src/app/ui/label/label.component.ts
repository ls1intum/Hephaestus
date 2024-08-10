import { Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from 'app/utils';
import { cva } from 'app/storybook.helper';

const [labelVariants, args, argTypes] = cva('text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70');

export { args, argTypes };

@Component({
  selector: 'app-label',
  standalone: true,
  templateUrl: './label.component.html'
})
export class LabelComponent {
  class = input<ClassValue>('');
  for = input<string>('');

  computedClass = computed(() => cn(labelVariants({}), this.class()));
}
