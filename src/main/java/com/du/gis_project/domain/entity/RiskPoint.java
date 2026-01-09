package com.du.gis_project.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "risk_points")
public class RiskPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double latitude;
    private double longitude;
    private double weight;

    @Enumerated(EnumType.STRING)
    private RiskType type;

    public RiskPoint() {
    }

    public RiskPoint(double latitude, double longitude, double weight, RiskType type) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.weight = weight;
        this.type = type;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public RiskType getType() {
        return type;
    }

    public void setType(RiskType type) {
        this.type = type;
    }
}
