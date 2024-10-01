import { Component, computed, input } from '@angular/core';
import { cn } from 'app/utils';
import { ClassValue } from 'clsx';

export type SelectOption = {
  id: string;
  value: string;
  label: string;
};

@Component({
  selector: 'app-skeleton',
  standalone: true,
  template: '',
  host: {
    '[class]': 'computedClass()'
  }
})
export class SkeletonComponent {
  class = input<ClassValue>('');

  computedClass = computed(() => cn('block animate-pulse rounded-md bg-muted', this.class()));
}
