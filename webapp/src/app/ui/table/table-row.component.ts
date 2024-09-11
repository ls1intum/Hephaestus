import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-row',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()',
    style: 'display: table-row;'
  }
})
export class TableRowComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted', this.class()));
}
