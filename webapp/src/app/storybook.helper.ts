// Source:
// https://stackoverflow.com/questions/78379300/how-do-i-use-angular-input-signals-with-storybook

import { InputSignalWithTransform, InputSignal, EventEmitter } from '@angular/core';

export function toArgs<Component>(
  args: Partial<TransformSignalInputType<TransformEventType<Component>>>
): TransformEventType<Component> {
  return args as unknown as TransformEventType<Component>;
}

/** Convert event emitter to callback for storybook */
type TransformEventType<T> = {
  [K in keyof T]: T[K] extends EventEmitter<infer E> ? (e: E) => void : T[K];
};

/** Convert any input signal into the held type of the signal */
type TransformSignalInputType<T> = {
  [K in keyof T]: TransformInputType<T[K]>;
};


// Type to extract the type from InputSignal or InputSignalWithTransform
type TransformInputType<T> =
  T extends InputSignalWithTransform<infer U, any>
      ? U
      : T extends InputSignal<infer U>
        ? U
        : T;
