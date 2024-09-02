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
import { HttpHeaders }                                       from '@angular/common/http';

import { Observable }                                        from 'rxjs';

import { PullRequest } from '../model/models';


import { Configuration }                                     from '../configuration';



export interface PullRequestServiceInterface {
    defaultHeaders: HttpHeaders;
    configuration: Configuration;

    /**
     * 
     * 
     * @param id 
     */
    getPullRequest(id: number, extraHttpRequestParams?: any): Observable<PullRequest>;

    /**
     * 
     * 
     * @param login 
     */
    getPullRequestsByAuthor(login: string, extraHttpRequestParams?: any): Observable<Set<PullRequest>>;

}
