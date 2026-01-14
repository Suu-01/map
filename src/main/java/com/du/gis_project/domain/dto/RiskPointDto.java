package com.du.gis_project.domain.dto;

import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.domain.entity.RiskType;

/**
 * 안전 시설물(CCTV, 경찰서, 가로등) 정보를 전달하는 DTO
 */
public class RiskPointDto {
    private double latitude;
    private double longitude;
    private double weight;
    private RiskType type;

    public RiskPointDto() {
    }

    public RiskPointDto(double latitude, double longitude, double weight, RiskType type) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.weight = weight;
        this.type = type;
    }

    // Entity -> DTO 변환 생성자
    public RiskPointDto(RiskPoint entity) {
        this.latitude = entity.getLatitude();
        this.longitude = entity.getLongitude();
        this.weight = entity.getWeight();
        this.type = entity.getType();
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
