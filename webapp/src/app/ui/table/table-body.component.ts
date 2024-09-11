import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-body',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()',
    style: 'display: table-row-group;'
  }
})
export class TableBodyComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('[&_tr:last-child]:border-0', this.class()));
}
