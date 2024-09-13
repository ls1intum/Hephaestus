import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-cell',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()',
    style: 'display: table-cell;'
  }
})
export class TableCellComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('p-4 align-middle [&:has([role=checkbox])]:pr-0', this.class()));
}
