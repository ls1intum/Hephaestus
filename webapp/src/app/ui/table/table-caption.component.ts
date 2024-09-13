import { Component, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Component({
  selector: 'app-table-caption',
  standalone: true,
  template: `<ng-content />`,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableCaptionComponent {
  class = input<ClassValue>();

  computedClass = computed(() => cn('table-caption mt-4 text-sm text-muted-foreground', this.class()));
}
