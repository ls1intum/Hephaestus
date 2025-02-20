import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'th[appTableHead]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableHeadDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('h-12 px-4 text-left align-middle font-medium text-muted-foreground [&:has([role=checkbox])]:pr-0', this.class()));
}
