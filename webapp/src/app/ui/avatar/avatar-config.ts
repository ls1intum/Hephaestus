import { inject, InjectionToken, Provider } from '@angular/core';

export interface AvatarConfig {
  delayMs: number;
}

export const defaultAvatarConfig: AvatarConfig = {
  delayMs: 0
};

export const AvatarConfigToken = new InjectionToken<AvatarConfig>('AvatarConfigToken');

export function provideAvatarConfig(config: Partial<AvatarConfig>): Provider[] {
  return [
    {
      provide: AvatarConfigToken,
      useValue: { ...defaultAvatarConfig, ...config }
    }
  ];
}

export function injectAvatarConfig(): AvatarConfig {
  return inject(AvatarConfigToken, { optional: true }) ?? defaultAvatarConfig;
}
