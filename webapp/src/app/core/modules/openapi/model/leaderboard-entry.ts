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
import { PullRequestReviewDTO } from './pull-request-review-dto';


export interface LeaderboardEntry { 
    githubName?: string;
    avatarUrl?: string;
    name?: string;
    type?: LeaderboardEntry.TypeEnum;
    score?: number;
    rank?: number;
    changesRequested?: Array<PullRequestReviewDTO>;
    approvals?: Array<PullRequestReviewDTO>;
    comments?: Array<PullRequestReviewDTO>;
}
export namespace LeaderboardEntry {
    export type TypeEnum = 'USER' | 'BOT';
    export const TypeEnum = {
        User: 'USER' as TypeEnum,
        Bot: 'BOT' as TypeEnum
    };
}


