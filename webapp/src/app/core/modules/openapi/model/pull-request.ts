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
import { Repository } from './repository';
import { PullRequestReview } from './pull-request-review';
import { User } from './user';
import { IssueComment } from './issue-comment';


export interface PullRequest { 
    id?: number;
    createdAt?: string;
    updatedAt?: string;
    title: string;
    url: string;
    /**
     * State of the PullRequest.  Does not include the state of the merge.
     */
    state: PullRequest.StateEnum;
    additions?: number;
    deletions?: number;
    commits?: number;
    changedFiles?: number;
    mergedAt?: string;
    author?: User;
    comments?: Set<IssueComment>;
    reviews?: Set<PullRequestReview>;
    repository?: Repository;
}
export namespace PullRequest {
    export type StateEnum = 'CLOSED' | 'OPEN';
    export const StateEnum = {
        Closed: 'CLOSED' as StateEnum,
        Open: 'OPEN' as StateEnum
    };
}


