import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'td[appTableCell]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableCellDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('p-4 align-middle [&:has([role=checkbox])]:pr-0', this.class()));
}
