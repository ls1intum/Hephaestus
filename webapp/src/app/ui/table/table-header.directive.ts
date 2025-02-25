import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'thead[appTableHeader]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableHeaderDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('w-full caption-bottom text-sm', this.class()));
}
