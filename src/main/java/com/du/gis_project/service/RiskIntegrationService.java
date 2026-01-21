package com.du.gis_project.service;

import com.du.gis_project.config.GisConfig;
import com.du.gis_project.domain.dto.HeatmapPointDto;
import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.repository.RiskPointRepository;
import com.du.gis_project.util.DistanceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class RiskIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(RiskIntegrationService.class);

    private final RiskPointRepository riskPointRepository;
    private final GisConfig gisConfig;

    public RiskIntegrationService(RiskPointRepository riskPointRepository,
            GisConfig gisConfig) {
        this.riskPointRepository = riskPointRepository;
        this.gisConfig = gisConfig;
    }

    // 설정 클래스에서 값을 가져와 사용
    private double getMinLat() {
        return gisConfig.getMap().getBounds().getMinLat();
    }

    private double getMaxLat() {
        return gisConfig.getMap().getBounds().getMaxLat();
    }

    private double getMinLon() {
        return gisConfig.getMap().getBounds().getMinLon();
    }

    private double getMaxLon() {
        return gisConfig.getMap().getBounds().getMaxLon();
    }

    private double getStepLat() {
        return gisConfig.getMap().getGrid().getStepLat();
    }

    private double getStepLon() {
        return gisConfig.getMap().getGrid().getStepLon();
    }

    // 성남시 법정동별 중심점 데이터 (도시 구역 판정용)
    private static final double[][] SEONGNAM_DONG_CENTERS = {
            { 37.441, 127.140 }, { 37.446, 127.146 }, { 37.438, 127.144 }, { 37.439, 127.126 },
            { 37.443, 127.129 }, { 37.440, 127.132 }, { 37.445, 127.133 }, { 37.436, 127.131 },
            { 37.438, 127.124 }, { 37.452, 127.158 }, { 37.456, 127.150 }, { 37.452, 127.165 },
            { 37.456, 127.127 }, { 37.429, 127.103 }, { 37.433, 127.098 }, { 37.436, 127.142 },
            { 37.442, 127.152 }, { 37.446, 127.162 }, { 37.450, 127.168 }, { 37.454, 127.164 },
            { 37.458, 127.169 }, { 37.439, 127.172 }, { 37.435, 127.165 }, { 37.431, 127.176 },
            { 37.428, 127.153 }, { 37.422, 127.162 }, { 37.368, 127.135 }, { 37.378, 127.113 },
            { 37.374, 127.119 }, { 37.366, 127.124 }, { 37.365, 127.106 }, { 37.358, 127.115 },
            { 37.352, 127.112 }, { 37.388, 127.132 }, { 37.381, 127.140 }, { 37.397, 127.127 },
            { 37.404, 127.120 }, { 37.408, 127.130 }, { 37.411, 127.122 }, { 37.418, 127.142 },
            { 37.391, 127.086 }, { 37.401, 127.111 }, { 37.387, 127.107 }, { 37.392, 127.054 }
    };

    /**
     * 해당 좌표가 성남지역(행정구역 근방)인지 판정합니다.
     */
    public boolean isInsideSeongnam(double lat, double lon) {
        // 법정동 중심점에서 2.5km 이내면 성남시 구역으로 인정 (도시 외곽 산악지대 포함)
        double coverageRadius = 2500.0;
        for (double[] center : SEONGNAM_DONG_CENTERS) {
            if (DistanceUtil.calculateDistance(lat, lon, center[0], center[1]) < coverageRadius) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> calculateRefinedRiskMap() {
        // [위험도 히트맵] 시설물 기반 계산 (도시 모양 정밀 쉐이핑 적용)
        return calculateGrid(2.0, 300.0);
    }

    private Map<String, Object> calculateGrid(double baseScore, double facilityRadius) {
        List<HeatmapPointDto> results = new ArrayList<>();

        // 1. 성남 지역 내 시설물만 필터링 (타 지역 마커가 계산에 포함되는 것 방지)
        List<RiskPoint> allFacilities = riskPointRepository.findAll();
        List<RiskPoint> facilities = new ArrayList<>();
        for (RiskPoint rp : allFacilities) {
            if (isInsideSeongnam(rp.getLatitude(), rp.getLongitude())) {
                facilities.add(rp);
            }
        }

        log.info("위험도 히트맵 계산 시작 (성남 내 시설물 필터링 적용). 시설 수: {}", facilities.size());

        // 위도/경도 평면을 격자로 순회하며 각 포인트의 점수 계산
        for (double lat = getMinLat(); lat <= getMaxLat(); lat += getStepLat()) {
            for (double lon = getMinLon(); lon <= getMaxLon(); lon += getStepLon()) {

                // 2. 성남 행정구역(동 중심점 기준) 바깥은 히트맵 생성 안함 (네모 형태 억제)
                if (!isInsideSeongnam(lat, lon)) {
                    continue;
                }

                double score = baseScore;

                for (RiskPoint rp : facilities) {
                    double dist = DistanceUtil.calculateDistance(lat, lon, rp.getLatitude(), rp.getLongitude());
                    if (dist < facilityRadius) {
                        double factor = 1.0 - (dist / facilityRadius);
                        score -= (rp.getWeight() * factor);
                    }
                }

                score = Math.max(0.0, Math.min(score, 3.0));
                results.add(new HeatmapPointDto(lat, lon, score));
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("result", results);
        return response;
    }
}
