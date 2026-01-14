package com.du.gis_project.domain.dto;

/**
 * 히트맵 그리드 포인트 정보를 전달하는 DTO
 */
public class HeatmapPointDto {
    private double lat;
    private double lon;
    private double score;

    public HeatmapPointDto() {
    }

    public HeatmapPointDto(double lat, double lon, double score) {
        this.lat = lat;
        this.lon = lon;
        this.score = score;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
