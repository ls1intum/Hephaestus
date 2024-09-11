import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-header',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()',
    style: 'display: table-header-group;'
  }
})
export class TableHeaderComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('w-full caption-bottom text-sm', this.class()));
}
