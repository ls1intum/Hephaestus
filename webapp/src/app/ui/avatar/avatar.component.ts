import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import type { ClassValue } from 'clsx';
import type { VariantProps } from 'class-variance-authority';
import { cn } from 'app/utils';
import { cva } from 'app/storybook.helper';
import { NgOptimizedImage } from '@angular/common';

const [avatarVariants, args, argTypes] = cva(
  'relative flex shrink-0 overflow-hidden rounded-full',
  {
    variants: {
      variant: {
        small: 'h-6 w-6 text-xs',
        medium: 'h-10 w-10',
        large: 'h-14 w-14 text-lg',
      },
    },
    defaultVariants: {
      variant: 'medium',
    },
  },
);

export { args, argTypes };

interface AvatarVariants extends VariantProps<typeof avatarVariants> {}

@Component({
  selector: 'app-avatar',
  standalone: true,
  imports: [NgOptimizedImage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  // template: `
  //   <ng-container *ngIf="image?.canShow(); else fallback">
  //     <ng-content
  //       select="[appAvatarImage]"
  //       (error)="onError()"
  //       (load)="onLoad()"
  //     />
  //   </ng-container>
  //   <ng-template #fallback>
  //     <ng-content select="[avatarFallback]" />
  //   </ng-template>
  // `,
  templateUrl: './avatar.component.html',
})
// export class AppAvatarComponent {
//   class = input<ClassValue>('');
//   variant = input<AvatarVariants['variant']>('medium');

//   @ContentChild(AvatarImageDirective, { static: true })
//   image: AvatarImageDirective | null = null;

//   computedClass = computed(() =>
//     cn(avatarVariants({ variant: this.variant() }), this.class()),
//   );
// }
export class AppAvatarComponent {
  class = input<ClassValue>('');
  variant = input<AvatarVariants['variant']>('medium');

  src = input<string>('');
  alt = input<string>('');
  imageClass = input<string>('');
  fallback = input<string>('');

  canShow = signal(true);

  onError = () => {
    if (this.fallback.length > 0) {
      this.src = this.fallback;
    } else {
      this.canShow.set(false);
    }
  };

  computedClass = computed(() =>
    cn(avatarVariants({ variant: this.variant() }), this.class()),
  );

  computedImageClass = computed(() =>
    cn('aspect-square object-cover h-full w-full', this.imageClass()),
  );
}
