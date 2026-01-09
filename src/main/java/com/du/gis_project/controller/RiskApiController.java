package com.du.gis_project.controller;

import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.domain.entity.RiskType;
import com.du.gis_project.service.CsvImportService;
import com.du.gis_project.service.RiskIntegrationService;
import com.du.gis_project.service.RiskService;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class RiskApiController {

    // VWorld API Key
    private final String VWORLD_KEY = "CF0C7D65-44C0-31CD-A6FF-80C2E693894A";

    private final CsvImportService csvImportService;
    private final RiskService riskService;
    private final RiskIntegrationService riskIntegrationService;

    public RiskApiController(CsvImportService csvImportService, RiskService riskService,
            RiskIntegrationService riskIntegrationService) {
        this.csvImportService = csvImportService;
        this.riskService = riskService;
        this.riskIntegrationService = riskIntegrationService;
    }

    // ============================
    // 1. Data Management Endpoints
    // ============================

    /**
     * Trigger CSV Import (CCTV, Police, Streetlight)
     */
    @PostMapping("/api/import")
    public Map<String, Object> importData() {
        try {
            csvImportService.importAllData();
            return Map.of("status", "OK", "message", "Import completed successfully");
        } catch (Exception e) {
            e.printStackTrace(); // Log to server console
            return Map.of(
                    "status", "ERROR",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown Error",
                    "class", e.getClass().getName());
        }
    }

    /**
     * Retrieve Risk Points
     * 
     * @param type Optional (ALL, CCTV, POLICE, STREET_LIGHT)
     */
    @GetMapping("/api/risks")
    public Map<String, Object> getRisks(@RequestParam(required = false) String type) {
        try {
            List<RiskPoint> risks;
            if (type == null || type.equals("ALL") || type.isEmpty()) {
                risks = riskService.getAllRisks();
            } else {
                risks = riskService.getRisksByType(RiskType.valueOf(type));
            }
            return Map.of("status", "OK", "result", risks);
        } catch (IllegalArgumentException e) {
            return Map.of("status", "ERROR", "message", "Invalid RiskType: " + type);
        } catch (Exception e) {
            return Map.of("status", "ERROR", "message", e.getMessage());
        }
    }

    /**
     * Retrieve Integrated Blind Spot Data
     */
    @GetMapping("/api/risks/blind-spots")
    public Map<String, Object> getBlindSpots() {
        try {
            List<Map<String, Object>> result = riskIntegrationService.calculateBlindSpots();
            return Map.of("status", "OK", "result", result);
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "ERROR", "message", e.getMessage());
        }
    }

    /**
     * Retrieve Refined Risk Heatmap Data
     */
    @GetMapping("/api/risks/refined-risk")
    public Map<String, Object> getRefinedRisk() {
        try {
            Map<String, Object> result = riskIntegrationService.calculateRefinedRiskMap();
            result.put("status", "OK");
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "ERROR", "message", e.getMessage());
        }
    }

    // ============================
    // 2. VWorld Proxy Endpoints (Existing)
    // ============================

    /**
     * 좌표로 주소 조회 (Reverse Geocoding)
     */
    @GetMapping("/api/proxy/address")
    public String getAddress(@RequestParam double lon, @RequestParam double lat) {
        String apiUrl = String.format(
                "https://api.vworld.kr/req/address?service=address&request=getAddress&version=2.0&crs=epsg:4326&point=%f,%f&format=json&type=both&key=%s",
                lon, lat, VWORLD_KEY);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        try {
            return restTemplate.getForObject(apiUrl, String.class);
        } catch (Exception e) {
            return "{\"response\":{\"status\":\"ERROR\"}}";
        }
    }

    /**
     * 통합 검색 (지능형 분석 및 결과 정밀화 - v5)
     */
    @GetMapping("/api/proxy/search")
    public String searchAddress(@RequestParam String address) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        String query = address.trim();

        // 1. 고도화된 주소 패턴 감지 (정규식 강화)
        // - 경기도, 성남시, 수정구 등 지역 명칭이 포함된 경우 주소로 판단
        boolean isAddressLikely = query.matches(".*[로길동리읍면]$") ||
                query.matches(".*[로길]\\s?\\d+.*") ||
                query.matches(".*\\d+번길.*") ||
                query.matches(".*\\d+-\\d+.*") ||
                query.contains("도 ") ||
                query.contains("시 ") ||
                query.contains("구 ") ||
                query.contains("지번") ||
                query.contains("대로");

        String response;

        if (isAddressLikely) {
            // [주소 우선 모드] address -> place -> district
            response = callVWorldSearch(query, "address", restTemplate);
            if (shouldRetry(response)) {
                response = callVWorldSearch(query, "place", restTemplate);
            }
        } else {
            // [장소 우선 모드] place -> address -> district
            response = callVWorldSearch(query, "place", restTemplate);
            if (shouldRetry(response)) {
                response = callVWorldSearch(query, "address", restTemplate);
            }
        }

        // 마지막 보루: 행정구역
        if (shouldRetry(response)) {
            response = callVWorldSearch(query, "district", restTemplate);
        }

        return response;
    }

    private String callVWorldSearch(String query, String type, RestTemplate restTemplate) {
        try {
            URI uri = UriComponentsBuilder.fromUriString("https://api.vworld.kr/req/search")
                    .queryParam("service", "search")
                    .queryParam("request", "search")
                    .queryParam("version", "2.0")
                    .queryParam("crs", "EPSG:4326")
                    .queryParam("query", query)
                    .queryParam("type", type)
                    .queryParam("format", "json")
                    .queryParam("key", VWORLD_KEY)
                    .build()
                    .encode()
                    .toUri();

            return restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            return String.format("{\"response\":{\"status\":\"ERROR\", \"message\":\"%s\"}}", e.getMessage());
        }
    }

    private boolean shouldRetry(String response) {
        if (response == null)
            return true;

        // 1. 상태값 체크
        if (response.contains("\"status\":\"NOT_FOUND\"") || response.contains("\"status\":\"ERROR\"")) {
            return true;
        }

        // 2. 검색 결과 개수 체크 (정규식으로 더 유연하게 탐지)
        // "total" : "0" 등 모든 형태 대응
        if (response.replaceAll("\\s", "").contains("\"total\":\"0\"") ||
                response.replaceAll("\\s", "").contains("\"total\":0")) {
            return true;
        }

        return false;
    }
}
