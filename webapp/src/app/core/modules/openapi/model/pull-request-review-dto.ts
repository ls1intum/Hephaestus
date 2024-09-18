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


export interface PullRequestReviewDTO { 
    id?: number;
    createdAt?: string;
    updatedAt?: string;
    submittedAt?: string;
    state?: PullRequestReviewDTO.StateEnum;
}
export namespace PullRequestReviewDTO {
    export type StateEnum = 'COMMENTED' | 'APPROVED' | 'CHANGES_REQUESTED' | 'DISMISSED';
    export const StateEnum = {
        Commented: 'COMMENTED' as StateEnum,
        Approved: 'APPROVED' as StateEnum,
        ChangesRequested: 'CHANGES_REQUESTED' as StateEnum,
        Dismissed: 'DISMISSED' as StateEnum
    };
}

