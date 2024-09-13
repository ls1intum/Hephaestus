import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-header',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableHeaderComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('table-header-group w-full caption-bottom text-sm', this.class()));
}
