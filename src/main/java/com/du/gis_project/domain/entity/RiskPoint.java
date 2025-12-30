package com.du.gis_project.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "risk_point")
public class RiskPoint {

    @Id
    @GeneratedValue
    private Long id;

    private double latitude;
    private double longitude;
    private double weight;

    @Enumerated(EnumType.STRING)
    private RiskType type;
}
