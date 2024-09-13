import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'tr[appTableRow]',
  standalone: true,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableRowDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted', this.class()));
}
