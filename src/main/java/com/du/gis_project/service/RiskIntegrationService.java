package com.du.gis_project.service;

import com.du.gis_project.config.GisConfig;
import com.du.gis_project.domain.dto.HeatmapPointDto;
import com.du.gis_project.domain.entity.PopulationPoint;
import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.repository.PopulationPointRepository;
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
    private final PopulationPointRepository populationPointRepository;
    private final GisConfig gisConfig;

    public RiskIntegrationService(RiskPointRepository riskPointRepository,
            PopulationPointRepository populationPointRepository,
            GisConfig gisConfig) {
        this.riskPointRepository = riskPointRepository;
        this.populationPointRepository = populationPointRepository;
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

    @SuppressWarnings("unchecked")
    public List<HeatmapPointDto> calculateBlindSpots() {
        // [통합 위험도 지도] 기본 2.0점, 인구 최대 1.0점 추가 체계 (총 3.0점 만점)
        return (List<HeatmapPointDto>) calculateGrid(2.0, 200, 0.5, 600, false).get("result");
    }

    public Map<String, Object> calculateRefinedRiskMap() {
        // [위험도 히트맵(정밀)] 사용자 원칙(2+1=3)에 따라 가중치 설정
        return calculateGrid(2.0, 200, 0.5, 700, true);
    }

    /**
     * 특정 파라미터에 따라 격자(Grid) 내 각 지점의 위험 점수를 계산합니다.
     */
    private Map<String, Object> calculateGrid(double baseScore, double facilityRadius,
            double popWeightMultiplier, double popRadius, boolean forceClipping) {
        List<HeatmapPointDto> results = new ArrayList<>();

        List<RiskPoint> facilities = riskPointRepository.findAll();
        List<PopulationPoint> popPoints = populationPointRepository.findAll();

        log.info("열화상 위험도 계산 시작 (설정 기반). 발견된 인구 데이터 수: {}", popPoints.size());

        boolean hasPopulation = !popPoints.isEmpty();
        double maxPop = hasPopulation ? popPoints.stream().mapToInt(PopulationPoint::getCount).max().orElse(1) : 1;

        // 위도/경도 평면을 격자로 순회하며 각 포인트의 점수 계산
        for (double lat = getMinLat(); lat <= getMaxLat(); lat += getStepLat()) {
            for (double lon = getMinLon(); lon <= getMaxLon(); lon += getStepLon()) {

                double score = baseScore;

                // 쉐이핑 로직: 인구가 전혀 없는 곳은 히트맵에서 제외
                if (forceClipping) {
                    if (!hasPopulation)
                        continue;
                    boolean nearPopCenter = false;
                    for (PopulationPoint pp : popPoints) {
                        if (DistanceUtil.calculateDistance(lat, lon, pp.getLatitude(), pp.getLongitude()) < 1200) {
                            nearPopCenter = true;
                            break;
                        }
                    }
                    if (!nearPopCenter)
                        continue;
                } else if (hasPopulation) {
                    boolean nearPopCenter = false;
                    for (PopulationPoint pp : popPoints) {
                        if (DistanceUtil.calculateDistance(lat, lon, pp.getLatitude(), pp.getLongitude()) < 1800) {
                            nearPopCenter = true;
                            break;
                        }
                    }
                    if (!nearPopCenter)
                        continue;
                }

                // 1. 치안 시설물에 의한 위험도 차감
                for (RiskPoint rp : facilities) {
                    double dist = DistanceUtil.calculateDistance(lat, lon, rp.getLatitude(), rp.getLongitude());
                    if (dist < facilityRadius) {
                        double factor = 1.0 - (dist / facilityRadius);
                        score -= (rp.getWeight() * factor);
                    }
                }

                // 2. 인구 밀집도에 의한 위험도 가산
                if (hasPopulation) {
                    for (PopulationPoint pp : popPoints) {
                        double dist = DistanceUtil.calculateDistance(lat, lon, pp.getLatitude(), pp.getLongitude());
                        if (dist < popRadius) {
                            double popRatio = (double) pp.getCount() / maxPop;
                            double popImpact = (popRatio * popWeightMultiplier * 2.0);
                            double distanceDecay = 1.0 - (dist / popRadius);
                            score += (popImpact * distanceDecay);
                        }
                    }
                }

                score = Math.max(0.0, Math.min(score, 3.0));

                results.add(new HeatmapPointDto(lat, lon, score));
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("result", results);
        response.put("popCount", popPoints.size());
        return response;
    }
}
