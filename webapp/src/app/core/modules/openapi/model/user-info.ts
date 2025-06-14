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


export interface UserInfo { 
    id: number;
    login: string;
    email?: string;
    avatarUrl: string;
    name: string;
    htmlUrl: string;
    leaguePoints?: number;
}

