import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'th[appTableHead]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableHeadDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('h-12 px-4 text-left align-middle font-medium text-muted-foreground [&:has([role=checkbox])]:pr-0', this.class()));
}
