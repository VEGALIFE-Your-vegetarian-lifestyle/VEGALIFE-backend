package com.vegalife.model.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "location")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "name", length = 255, nullable = false)
  private String name;

  @Column(name = "address", length = 255, nullable = false)
  private String address;

  @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
  private BigDecimal latitude;

  @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
  private BigDecimal longitude;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "place_type", length = 20, nullable = false)
  private PlaceType placeType;

  @Column(name = "opening_hours", columnDefinition = "jsonb")
  private String openingHours;

  @Column(name = "website_url", columnDefinition = "TEXT")
  private String websiteUrl;

  @Column(name = "rating", precision = 2, scale = 1)
  private BigDecimal rating;

  @Column(name = "price_level")
  private Integer priceLevel;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public enum PlaceType {
    restaurant,
    grocery,
    cafe,
    market
  }
}
