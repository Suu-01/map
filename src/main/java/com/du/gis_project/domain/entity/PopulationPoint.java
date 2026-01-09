package com.du.gis_project.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "population_points")
public class PopulationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String district; // 구
    private String dong; // 동
    private int count; // 인구수

    private double latitude;
    private double longitude;

    public PopulationPoint() {
    }

    public PopulationPoint(String district, String dong, int count, double latitude, double longitude) {
        this.district = district;
        this.dong = dong;
        this.count = count;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDong() {
        return dong;
    }

    public void setDong(String dong) {
        this.dong = dong;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
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
}
