import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'tbody[appTableBody]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableBodyDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('[&_tr:last-child]:border-0', this.class()));
}
