package de.tum.in.www1.hephaestus.hello;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table
@Getter
@Setter
public class Hello {

  /**
   * The unique identifier for a Hello entity.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * The timestamp of when the Hello entity was created.
   * This field is mandatory.
   */
  @Column(nullable = false)
  private Instant timestamp;
}
