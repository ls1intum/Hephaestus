export * from './admin.service';
import { AdminService } from './admin.service';
export * from './admin.serviceInterface';
export * from './hello.service';
import { HelloService } from './hello.service';
export * from './hello.serviceInterface';
export const APIS = [AdminService, HelloService];
