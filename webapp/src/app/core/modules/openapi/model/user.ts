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
import { PullRequestReviewComment } from './pull-request-review-comment';
import { PullRequestReview } from './pull-request-review';
import { PullRequest } from './pull-request';
import { IssueComment } from './issue-comment';


export interface User { 
    id?: number;
    createdAt?: string;
    updatedAt?: string;
    /**
     * Unique login identifier for a user.
     */
    login: string;
    email?: string;
    /**
     * Display name of the user.
     */
    name?: string;
    /**
     * Unique URL to the user\'s profile.  Not the website a user can set in their profile.
     */
    url: string;
    /**
     * URL to the user\'s avatar.  If unavailable, a fallback can be generated from the login, e.g. on Github:  https://github.com/{login}.png
     */
    avatarUrl?: string;
    pullRequests?: Set<PullRequest>;
    issueComments?: Set<IssueComment>;
    reviewComments?: Set<PullRequestReviewComment>;
    reviews?: Set<PullRequestReview>;
}

