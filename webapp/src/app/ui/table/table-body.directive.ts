import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'tbody[appTableBody]',
  standalone: true,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableBodyDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('[&_tr:last-child]:border-0', this.class()));
}
