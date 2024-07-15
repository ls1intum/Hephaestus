/**
 * PokéAPI
 * All the Pokémon data you\'ll ever need in one place, easily accessible through a modern free open-source RESTful API.  ## What is this?  This is a full RESTful API linked to an extensive database detailing everything about the Pokémon main game series.  We\'ve covered everything from Pokémon to Berry Flavors.  ## Where do I start?  We have awesome [documentation](https://pokeapi.co/docs/v2) on how to use this API. It takes minutes to get started.  This API will always be publicly available and will never require any extensive setup process to consume.  Created by [**Paul Hallett**(]https://github.com/phalt) and other [**PokéAPI contributors***](https://github.com/PokeAPI/pokeapi#contributing) around the world. Pokémon and Pokémon character names are trademarks of Nintendo.     
 *
 * The version of the OpenAPI document: 2.7.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { HttpHeaders }                                       from '@angular/common/http';

import { Observable }                                        from 'rxjs';

import { EncounterConditionDetail } from '../model/models';
import { EncounterConditionValueDetail } from '../model/models';
import { EncounterMethodDetail } from '../model/models';
import { PaginatedEncounterConditionSummaryList } from '../model/models';
import { PaginatedEncounterConditionValueSummaryList } from '../model/models';
import { PaginatedEncounterMethodSummaryList } from '../model/models';
import { PokemonEncountersRetrieve200ResponseInner } from '../model/models';


import { Configuration }                                     from '../configuration';



export interface EncountersServiceInterface {
    defaultHeaders: HttpHeaders;
    configuration: Configuration;

    /**
     * List encounter conditions
     * Conditions which affect what pokemon might appear in the wild, e.g., day or night.
     * @param limit Number of results to return per page.
     * @param offset The initial index from which to return the results.
     * @param q &gt; Only available locally and not at [pokeapi.co](https://pokeapi.co/docs/v2) Case-insensitive query applied on the &#x60;name&#x60; property. 
     */
    encounterConditionList(limit?: number, offset?: number, q?: string, extraHttpRequestParams?: any): Observable<PaginatedEncounterConditionSummaryList>;

    /**
     * Get encounter condition
     * Conditions which affect what pokemon might appear in the wild, e.g., day or night.
     * @param id This parameter can be a string or an integer.
     */
    encounterConditionRetrieve(id: string, extraHttpRequestParams?: any): Observable<EncounterConditionDetail>;

    /**
     * List encounter condition values
     * Encounter condition values are the various states that an encounter condition can have, i.e., time of day can be either day or night.
     * @param limit Number of results to return per page.
     * @param offset The initial index from which to return the results.
     * @param q &gt; Only available locally and not at [pokeapi.co](https://pokeapi.co/docs/v2) Case-insensitive query applied on the &#x60;name&#x60; property. 
     */
    encounterConditionValueList(limit?: number, offset?: number, q?: string, extraHttpRequestParams?: any): Observable<PaginatedEncounterConditionValueSummaryList>;

    /**
     * Get encounter condition value
     * Encounter condition values are the various states that an encounter condition can have, i.e., time of day can be either day or night.
     * @param id This parameter can be a string or an integer.
     */
    encounterConditionValueRetrieve(id: string, extraHttpRequestParams?: any): Observable<EncounterConditionValueDetail>;

    /**
     * List encounter methods
     * Methods by which the player might can encounter Pokémon in the wild, e.g., walking in tall grass. Check out Bulbapedia for greater detail.
     * @param limit Number of results to return per page.
     * @param offset The initial index from which to return the results.
     * @param q &gt; Only available locally and not at [pokeapi.co](https://pokeapi.co/docs/v2) Case-insensitive query applied on the &#x60;name&#x60; property. 
     */
    encounterMethodList(limit?: number, offset?: number, q?: string, extraHttpRequestParams?: any): Observable<PaginatedEncounterMethodSummaryList>;

    /**
     * Get encounter method
     * Methods by which the player might can encounter Pokémon in the wild, e.g., walking in tall grass. Check out Bulbapedia for greater detail.
     * @param id This parameter can be a string or an integer.
     */
    encounterMethodRetrieve(id: string, extraHttpRequestParams?: any): Observable<EncounterMethodDetail>;

    /**
     * Get pokemon encounter
     * Handles Pokemon Encounters as a sub-resource.
     * @param pokemonId 
     */
    pokemonEncountersRetrieve(pokemonId: string, extraHttpRequestParams?: any): Observable<Array<PokemonEncountersRetrieve200ResponseInner>>;

}
