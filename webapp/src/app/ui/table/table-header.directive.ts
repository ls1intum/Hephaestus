import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'thead[appTableHeader]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableHeaderDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('w-full caption-bottom text-sm', this.class()));
}
