export * from './admin-controller.service';
import { AdminControllerService } from './admin-controller.service';
export * from './admin-controller.serviceInterface';
export * from './hello-controller.service';
import { HelloControllerService } from './hello-controller.service';
export * from './hello-controller.serviceInterface';
export const APIS = [AdminControllerService, HelloControllerService];
