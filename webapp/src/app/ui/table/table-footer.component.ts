import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-footer',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableFooterComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('table-footer-group border-t bg-muted/50 font-medium [&>tr]:last:border-b-0', this.class()));
}
