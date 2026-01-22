package com.du.gis_project.controller;

import com.du.gis_project.domain.dto.RiskPointDto;
import com.du.gis_project.domain.entity.RiskType;
import com.du.gis_project.service.CsvImportService;
import com.du.gis_project.service.RiskService;
import com.du.gis_project.service.RiskIntegrationService;
import com.du.gis_project.config.GisConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class RiskApiController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RiskApiController.class);

    private final CsvImportService csvImportService;
    private final RiskService riskService;
    private final RiskIntegrationService riskIntegrationService;
    private final GisConfig gisConfig;

    public RiskApiController(CsvImportService csvImportService, RiskService riskService,
            RiskIntegrationService riskIntegrationService, GisConfig gisConfig) {
        this.csvImportService = csvImportService;
        this.riskService = riskService;
        this.riskIntegrationService = riskIntegrationService;
        this.gisConfig = gisConfig;
    }

    /**
     * CSV 데이터 임포트 실행
     */
    @PostMapping("/api/import")
    public ResponseEntity<Map<String, Object>> importData() {
        Map<String, Object> result = new HashMap<>();
        try {
            csvImportService.importAllData();
            result.put("status", "OK");
            result.put("message", "데이터 임포트가 완료되었습니다.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in data import: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 안전 시설물 데이터 조회 (CCTV, 경찰서, 가로등)
     */
    @GetMapping("/api/risks")
    public ResponseEntity<Map<String, Object>> getRisks(@RequestParam(required = false) RiskType type) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<RiskPointDto> list;
            if (type != null) {
                list = riskService.getRisksByType(type);
            } else {
                list = riskService.getAllRisks();
            }
            result.put("status", "OK");
            result.put("result", list);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in getRisks: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 정밀 위험도 히트맵 데이터 조회
     */
    @GetMapping("/api/risks/refined-risk")
    public ResponseEntity<Map<String, Object>> getRefinedRisk() {
        try {
            Map<String, Object> heatmapData = riskIntegrationService.calculateRefinedRiskMap();
            heatmapData.put("status", "OK");
            return ResponseEntity.ok(heatmapData);
        } catch (Exception e) {
            log.error("Error in getRefinedRisk: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 클라이언트 설정을 반환합니다.
     */
    @GetMapping("/api/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        try {
            Map<String, Object> config = new HashMap<>();

            Map<String, Object> vworld = new HashMap<>();
            vworld.put("key", gisConfig.getVworld().getKey());
            config.put("vworld", vworld);

            Map<String, Object> map = new HashMap<>();
            Map<String, Object> center = new HashMap<>();
            center.put("lon", gisConfig.getMap().getCenter().getLon());
            center.put("lat", gisConfig.getMap().getCenter().getLat());
            map.put("center", center);
            config.put("map", map);

            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error in getConfig: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 좌표로 주소 조회 (Reverse Geocoding)
     */
    @GetMapping("/api/proxy/address")
    public ResponseEntity<Map<String, Object>> getAddress(@RequestParam double lon, @RequestParam double lat) {
        String apiKey = gisConfig.getVworld().getKey();
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> result = new HashMap<>();

        try {
            // RestTemplate 템플릿 방식 사용하여 인코딩 호환성 높임
            String url = "https://api.vworld.kr/req/address?service=address&request=getAddress&version=2.0&crs=epsg:4326&point={point}&format=json&type=both&key={key}";

            Map<String, String> params = new HashMap<>();
            params.put("point", String.format("%.7f,%.7f", lon, lat));
            params.put("key", apiKey);

            log.info("VWorld GetAddress Calling with lat={}, lon={}", lat, lon);
            String response = restTemplate.getForObject(url, String.class, params);

            result.put("status", "OK");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in getAddress proxy: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 주소 검색 (VWorld 검색 API 2.0 프록시 - 초정밀 Smart 4단계 파이프라인)
     */
    @GetMapping("/api/proxy/search")
    public ResponseEntity<Map<String, Object>> searchAddress(@RequestParam String address) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> result = new HashMap<>();
        try {
            String apiKey = gisConfig.getVworld().getKey();
            String query = address != null ? address.trim() : "";
            if (query.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "검색어가 비어있습니다."));
            }

            // 1단계: 장소(place) 검색
            String response = callVWorldSearch(restTemplate, apiKey, "place", null, query);
            String foundType = "place";

            // 2단계: 도로명 주소(road) 검색 - 원본 쿼리
            if (isNotFound(response)) {
                response = callVWorldSearch(restTemplate, apiKey, "address", "road", query);
                foundType = "road";
            }

            // 2.5단계: 도로명 주소(road) 검색 - 정제된 쿼리 (괄호 제거 및 도로명만 추출)
            if (isNotFound(response)) {
                String cleanQuery = refineRoadQuery(query);
                if (!cleanQuery.equals(query)) {
                    response = callVWorldSearch(restTemplate, apiKey, "address", "road", cleanQuery);
                    foundType = "road";
                }
            }

            // 3단계: 지번 주소(parcel) 검색 - 원본 쿼리
            if (isNotFound(response)) {
                response = callVWorldSearch(restTemplate, apiKey, "address", "parcel", query);
                foundType = "parcel";
            }

            // 3.5단계: 지번 주소(parcel) 검색 - 정제된 쿼리 (동+지번만 추출)
            if (isNotFound(response)) {
                String cleanParcel = refineParcelQuery(query);
                if (!cleanParcel.equals(query)) {
                    response = callVWorldSearch(restTemplate, apiKey, "address", "parcel", cleanParcel);
                    foundType = "parcel";
                }
            }

            // 4단계: 행정구역(district) 검색
            if (isNotFound(response)) {
                response = callVWorldSearch(restTemplate, apiKey, "district", null, query);
                foundType = "district";
            }

            // 최종 결과 확인
            if (isNotFound(response)) {
                log.warn("All search stages failed for query: {}", query);
            }

            result.put("status", "OK");
            result.put("data", response);
            result.put("foundType", foundType);
            result.put("query", query);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in Smart searchAddress proxy: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    private String callVWorldSearch(RestTemplate restTemplate, String apiKey, String type, String category,
            String query) {
        // RestTemplate 템플릿 방식 사용하여 인코딩 자동 처리 (+ vs %20 문제 해결)
        String url = "https://api.vworld.kr/req/search?service=search&request=search&version=2.0&crs=epsg:3857&size=1&type={type}&query={query}&key={key}";
        if (category != null) {
            url += "&category=" + category;
        }

        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        params.put("query", query);
        params.put("key", apiKey);

        log.info("VWorld API [{}][{}] Calling for: {}", type, (category != null ? category : "-"), query);
        try {
            return restTemplate.getForObject(url, String.class, params);
        } catch (Exception e) {
            log.error("VWorld API Call Exception: {}", e.getMessage());
            return null;
        }
    }

    private String refineRoadQuery(String query) {
        // 1. 괄호 내용 제거: "수정로 100 (여수동)" -> "수정로 100"
        String cleaned = query.replaceAll("\\s*\\([^)]*\\)", "").trim();

        // 2. 도로명 주소 패턴 추출 시도: "성남시 중원구 성남대로 997" -> "성남대로 997"
        // (VWorld는 시/도 정보가 너무 길면 오히려 검색 실패할 때가 있음)
        Pattern roadPattern = Pattern.compile("([가-힣a-zA-Z0-9·]+([로|길]))\\s*(\\d+[-]?\\d*)");
        Matcher matcher = roadPattern.matcher(cleaned);
        if (matcher.find()) {
            return matcher.group(0).trim();
        }

        return cleaned;
    }

    private String refineParcelQuery(String query) {
        String cleaned = query.replaceAll("\\s*\\([^)]*\\)", "").trim();

        // 지번 주소 패턴 추출 시도: "성남시 분당구 삼평동 717" -> "삼평동 717"
        Pattern parcelPattern = Pattern.compile("([가-힣0-9]+[동|리|읍|면])\\s*(\\d+[-]?\\d*)");
        Matcher matcher = parcelPattern.matcher(cleaned);
        if (matcher.find()) {
            return matcher.group(0).trim();
        }

        return cleaned;
    }

    private boolean isNotFound(String response) {
        return response == null || response.contains("\"status\":\"NOT_FOUND\"")
                || response.contains("\"total\":\"0\"");
    }
}
