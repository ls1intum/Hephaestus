import { InputSignalWithTransform, InputSignal, EventEmitter } from '@angular/core';
import { Args, ArgTypes } from '@storybook/angular';
import { cva as CVA } from 'class-variance-authority';
import { ClassProp, ClassValue, StringToBoolean } from 'class-variance-authority/types';

// Source:
// https://stackoverflow.com/questions/78379300/how-do-i-use-angular-input-signals-with-storybook
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



// CVA Storybook Helper
type ConfigSchema = Record<string, Record<string, ClassValue>>;
type ConfigVariants<T extends ConfigSchema> = {
    [Variant in keyof T]?: StringToBoolean<keyof T[Variant]> | null | undefined;
};
type ConfigVariantsMulti<T extends ConfigSchema> = {
    [Variant in keyof T]?: StringToBoolean<keyof T[Variant]> | StringToBoolean<keyof T[Variant]>[] | undefined;
};
type Config<T> = T extends ConfigSchema ? {
    variants?: T;
    defaultVariants?: ConfigVariants<T>;
    compoundVariants?: (T extends ConfigSchema ? (ConfigVariants<T> | ConfigVariantsMulti<T>) & ClassProp : ClassProp)[];
} : never;

function createCVAArgTypes<T extends ConfigSchema>(config?: Config<T>) {
  if (!config?.variants) {
    return {};
  }

  const variants = config?.variants;
  return Object.fromEntries(
    Object.entries(variants).map(([variant, options]) => {
      const variantKey = variant as keyof T;
      const optionsArray = Object.keys(options) as (keyof T[typeof variantKey])[];
      return [variant, {
        control: { type: 'select' },
        options: optionsArray
      }];
    })
  );
}

function createCVADefaultArgs<T>(config?: Config<T>) {
  return (config?.defaultVariants || {}) as {
    [Variant in keyof T]: keyof T[Variant];
  }
}

export function cva<T extends ConfigSchema>(base?: ClassValue, config?: Config<T>) {
  const cvaFunction = CVA(base, config);
  const argTypes = createCVAArgTypes(config);
  const defaultArgs = createCVADefaultArgs(config);

  return [cvaFunction, defaultArgs, argTypes] as const;
}
