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
import { HttpHeaders }                                       from '@angular/common/http';

import { Observable }                                        from 'rxjs';

import { Contributor } from '../model/models';
import { MetaData } from '../model/models';


import { Configuration }                                     from '../configuration';



export interface MetaServiceInterface {
    defaultHeaders: HttpHeaders;
    configuration: Configuration;

    /**
     * 
     * 
     */
    getContributors(extraHttpRequestParams?: any): Observable<Array<Contributor>>;

    /**
     * 
     * 
     */
    getMetaData(extraHttpRequestParams?: any): Observable<MetaData>;

}
