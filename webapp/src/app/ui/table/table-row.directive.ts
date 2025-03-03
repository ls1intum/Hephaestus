import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Directive({
  selector: 'tr[appTableRow]',
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableRowDirective {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted', this.class()));
}
