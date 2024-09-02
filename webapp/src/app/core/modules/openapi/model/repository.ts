/**
 * Hephaestus API
 * API documentation for the Hephaestus application server.
 *
 * The version of the OpenAPI document: 0.0.1
 * Contact: felixtj.dietrich@tum.de
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { PullRequest } from './pull-request';


export interface Repository { 
    id?: number;
    createdAt?: string;
    updatedAt?: string;
    name: string;
    nameWithOwner: string;
    description: string;
    defaultBranch: string;
    visibility: Repository.VisibilityEnum;
    url: string;
    homepage?: string;
    pullRequests?: Set<PullRequest>;
}
export namespace Repository {
    export type VisibilityEnum = 'PUBLIC' | 'PRIVATE';
    export const VisibilityEnum = {
        Public: 'PUBLIC' as VisibilityEnum,
        Private: 'PRIVATE' as VisibilityEnum
    };
}

