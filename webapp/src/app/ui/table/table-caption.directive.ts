import { Directive, computed, input } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';

@Directive({
  selector: 'caption[appTableCaption]',
  standalone: true,
  host: {
    '[class]': 'computedClass()'
  }
})
export class TableCaptionDirective {
  class = input<ClassValue>();
  computedClass = computed(() => cn('mt-4 text-sm text-muted-foreground', this.class()));
}
