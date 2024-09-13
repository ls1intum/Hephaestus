import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'td[appTableCell]',
  standalone: true,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableCellDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('p-4 align-middle [&:has([role=checkbox])]:pr-0', this.class()));
}
