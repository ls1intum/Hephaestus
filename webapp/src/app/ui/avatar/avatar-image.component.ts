import { NgOptimizedImage } from '@angular/common';
import { AfterViewInit, Component, computed, ElementRef, input, OnInit, viewChild } from '@angular/core';
import { ClassValue } from 'clsx';
import { cn } from 'app/utils';
import { injectAvatar } from './avatar.component';

@Component({
  selector: 'app-avatar-image',
  standalone: true,
  imports: [NgOptimizedImage],
  template: `<img #imgEl [class]="computedClass()" [ngSrc]="src()" [alt]="alt()" (load)="onLoad()" (error)="onError()" fill />`,
  host: {
    '[class]': 'computedClass()',
    '[style.display]': 'avatar.state() === "error" ? "none" : "inline"'
  }
})
export class AvatarImageComponent implements OnInit, AfterViewInit {
  readonly avatar = injectAvatar();
  private readonly imgRef = viewChild<ElementRef<HTMLImageElement>>('imgEl');

  class = input<ClassValue>();
  src = input.required<string>();
  alt = input<string>('');

  computedClass = computed(() => cn('aspect-square h-full w-full', this.class()));

  ngOnInit(): void {
    this.avatar.setState('loading');
  }

  ngAfterViewInit(): void {
    if (!this.imgRef()?.nativeElement.src) {
      this.avatar.setState('error');
    }

    if (this.imgRef()?.nativeElement.complete) {
      this.avatar.setState('loaded');
    }
  }

  onLoad(): void {
    this.avatar.setState('loaded');
  }

  onError(): void {
    this.avatar.setState('error');
  }
}
