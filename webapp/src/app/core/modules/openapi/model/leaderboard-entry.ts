/**
 * Hephaestus API
 * API documentation for the Hephaestus application server.
 *
 * The version of the OpenAPI document: 0.9.0-rc.5
 * Contact: felixtj.dietrich@tum.de
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { PullRequestInfo } from './pull-request-info';
import { UserInfo } from './user-info';


export interface LeaderboardEntry { 
    rank: number;
    score: number;
    user: UserInfo;
    reviewedPullRequests: Array<PullRequestInfo>;
    numberOfReviewedPRs: number;
    numberOfApprovals: number;
    numberOfChangeRequests: number;
    numberOfComments: number;
    numberOfUnknowns: number;
    numberOfCodeComments: number;
}

