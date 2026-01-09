package com.du.gis_project.service;

import com.du.gis_project.domain.entity.PopulationPoint;
import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.repository.PopulationPointRepository;
import com.du.gis_project.repository.RiskPointRepository;
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

    public RiskIntegrationService(RiskPointRepository riskPointRepository,
            PopulationPointRepository populationPointRepository) {
        this.riskPointRepository = riskPointRepository;
        this.populationPointRepository = populationPointRepository;
    }

    // Seongnam Bounds (Approx)
    private static final double MIN_LAT = 37.330;
    private static final double MAX_LAT = 37.490;
    private static final double MIN_LON = 127.050;
    private static final double MAX_LON = 127.180;

    // Optimized density (~75m step) for best visibility/performance balance
    private static final double STEP_LAT = 0.00067;
    private static final double STEP_LON = 0.00082;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> calculateBlindSpots() {
        // [Default Map] Refine facility radius to 200m for tighter safe zones
        return (List<Map<String, Object>>) calculateGrid(2.5, 200, 0.8, 600, false).get("result");
    }

    public Map<String, Object> calculateRefinedRiskMap() {
        // [Refined Map] Reduce facility radius to 200m, increase pop weight slightly
        // for contrast
        return calculateGrid(2.0, 200, 1.2, 700, true);
    }

    private Map<String, Object> calculateGrid(double baseScore, double facilityRadius,
            double popWeightMultiplier, double popRadius, boolean forceClipping) {
        List<Map<String, Object>> results = new ArrayList<>();

        List<RiskPoint> facilities = riskPointRepository.findAll();
        List<PopulationPoint> popPoints = populationPointRepository.findAll();

        log.info("Calculating Thermal Risk Map. Population Points Found: {}", popPoints.size());

        boolean hasPopulation = !popPoints.isEmpty();
        double maxPop = hasPopulation ? popPoints.stream().mapToInt(PopulationPoint::getCount).max().orElse(1) : 1;

        for (double lat = MIN_LAT; lat <= MAX_LAT; lat += STEP_LAT) {
            for (double lon = MIN_LON; lon <= MAX_LON; lon += STEP_LON) {

                double score = baseScore;

                // Shaping Logic: Strict clip to residential areas (1200m)
                if (forceClipping) {
                    if (!hasPopulation)
                        continue; // No population = No shape

                    boolean nearPopCenter = false;
                    for (PopulationPoint pp : popPoints) {
                        if (calculateDistance(lat, lon, pp.getLatitude(), pp.getLongitude()) < 1200) {
                            nearPopCenter = true;
                            break;
                        }
                    }
                    if (!nearPopCenter)
                        continue;
                } else if (hasPopulation) {
                    // Optional shaping for old map
                    boolean nearPopCenter = false;
                    for (PopulationPoint pp : popPoints) {
                        if (calculateDistance(lat, lon, pp.getLatitude(), pp.getLongitude()) < 1800) {
                            nearPopCenter = true;
                            break;
                        }
                    }
                    if (!nearPopCenter)
                        continue;
                }

                // 1. Subtract Safety Facilities (Effect range: 200m)
                for (RiskPoint rp : facilities) {
                    double dist = calculateDistance(lat, lon, rp.getLatitude(), rp.getLongitude());
                    if (dist < facilityRadius) {
                        double factor = 1.0 - (dist / facilityRadius);
                        // Significant reduction near facility to ensure 'Blue' (low score)
                        score -= (rp.getWeight() * factor * 1.5);
                    }
                }

                // 2. Add Population Factor (Risk added in dense areas)
                if (hasPopulation) {
                    for (PopulationPoint pp : popPoints) {
                        double dist = calculateDistance(lat, lon, pp.getLatitude(), pp.getLongitude());
                        if (dist < popRadius) {
                            double popRatio = (double) pp.getCount() / maxPop;
                            double popImpact = (popRatio * popWeightMultiplier * 2.0);
                            double distanceDecay = 1.0 - (dist / popRadius);
                            score += (popImpact * distanceDecay);
                        }
                    }
                }

                // Clamp score (0.0=Safety/Blue, 8.0=Danger/Red)
                score = Math.max(0.0, Math.min(score, 8.0));

                Map<String, Object> point = new HashMap<>();
                point.put("lat", lat);
                point.put("lon", lon);
                point.put("score", score);
                results.add(point);
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("result", results);
        response.put("popCount", popPoints.size());
        return response;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000 * c;
    }
}
