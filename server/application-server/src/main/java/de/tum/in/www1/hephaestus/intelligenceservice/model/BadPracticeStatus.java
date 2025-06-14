/*
 * Hephaestus Intelligence Service API
 * API documentation for the Hephaestus Intelligence Service.
 *
 * The version of the OpenAPI document: 0.9.0-rc.5
 * Contact: felixtj.dietrich@tum.de
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package de.tum.in.www1.hephaestus.intelligenceservice.model;

import java.util.Objects;
import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.validator.constraints.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets BadPracticeStatus
 */
public enum BadPracticeStatus {
  
  GOOD_PRACTICE("Good Practice"),
  
  FIXED("Fixed"),
  
  CRITICAL_ISSUE("Critical Issue"),
  
  NORMAL_ISSUE("Normal Issue"),
  
  MINOR_ISSUE("Minor Issue"),
  
  WON_T_FIX("Won't Fix"),
  
  WRONG("Wrong");

  private String value;

  BadPracticeStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static BadPracticeStatus fromValue(String value) {
    for (BadPracticeStatus b : BadPracticeStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

