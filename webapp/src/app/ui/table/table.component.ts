import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table',
  standalone: true,
  template: `<table [class]="computedClass()">
    <ng-content />
  </table>`,
  host: {
    class: 'relative w-full overflow-auto'
  }
})
export class TableComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('w-full caption-bottom text-sm', this.class()));
}
