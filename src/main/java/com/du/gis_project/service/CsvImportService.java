package com.du.gis_project.service;

import com.du.gis_project.config.GisConfig;
import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.domain.entity.RiskType;
import com.du.gis_project.repository.RiskPointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private final RiskPointRepository riskPointRepository;
    private final GisConfig gisConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CsvImportService(RiskPointRepository riskPointRepository,
            GisConfig gisConfig) {
        this.riskPointRepository = riskPointRepository;
        this.gisConfig = gisConfig;
    }

    @Transactional
    public void importAllData() {
        log.info("Starting ALL data import...");

        // 1. CCTV (MS949)
        importCctv();

        // 2. Police (UTF-8)
        importPolice();

        // 3. Streetlight (UTF-8, Geocoding required)
        importStreetlight();

        log.info("ALL data import completed.");
    }

    @Transactional // Separate transaction for each type to avoid total rollback on partial fail
    public void importCctv() {
        // CCTV: index 3(lat), 4(lon), MS949
        log.info("Importing CCTV data...");
        riskPointRepository.deleteByType(RiskType.CCTV);
        // Use ClassPathResource to load from classpath (works in IDE and JAR)
        // Auto-detect columns (pass -2)
        importFile("static/data/cctv.csv", Charset.forName("MS949"), RiskType.CCTV, -2, -2, -1, false);
    }

    @Transactional
    public void importPolice() {
        // Police: index 1(lat), 0(lon), UTF-8
        log.info("Importing Police data...");
        riskPointRepository.deleteByType(RiskType.POLICE);
        importFile("static/data/police.csv", StandardCharsets.UTF_8, RiskType.POLICE, 1, 0, -1, false);
    }

    @Transactional
    public void importStreetlight() {
        log.info("Importing Streetlight data (from CSV coordinates)...");
        riskPointRepository.deleteByType(RiskType.STREET_LIGHT);

        // Files now contains Latitude/Longitude columns. Use auto-detect (-2).
        importFile("static/data/streetlight.csv", StandardCharsets.UTF_8, RiskType.STREET_LIGHT, -2, -2, -1, false);
    }

    private void importFile(String resourcePath, Charset charset, RiskType type, int latIdx, int lonIdx, int addrIdx,
            boolean useGeocoding) {
        List<RiskPoint> points = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resourcePath).getInputStream(), charset))) {
            String line;
            boolean isFirst = true;

            log.info("Reading file: {}", resourcePath);

            while ((line = br.readLine()) != null) {
                if (isFirst) {
                    isFirst = false;
                    continue; // Skip header
                }

                try {
                    // Simple CSV splitting
                    String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                    double latitude = 0.0;
                    double longitude = 0.0;

                    // 1. Try to find Lat/Lon in columns (Hybrid approach)
                    double autoLat = 0.0;
                    double autoLon = 0.0;
                    boolean foundCoords = false;

                    // Helper to check value range
                    int foundLatIdx = latIdx;
                    int foundLonIdx = lonIdx;

                    // Check for auto-detect or valid index
                    if (foundLatIdx == -2 || foundLonIdx == -2 || useGeocoding) {
                        for (int i = 0; i < cols.length; i++) {
                            try {
                                String valStr = cols[i].replace("\"", "").trim();
                                if (valStr.isEmpty())
                                    continue;
                                double val = Double.parseDouble(valStr);
                                if (val >= 33 && val <= 43)
                                    foundLatIdx = i;
                                else if (val >= 124 && val <= 132)
                                    foundLonIdx = i;
                            } catch (NumberFormatException e) {
                                /* ignore */ }
                        }
                    }

                    if (foundLatIdx >= 0 && foundLonIdx >= 0 && foundLatIdx < cols.length
                            && foundLonIdx < cols.length) {
                        try {
                            String latStr = cols[foundLatIdx].replace("\"", "").trim();
                            String lonStr = cols[foundLonIdx].replace("\"", "").trim();
                            if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                                autoLat = Double.parseDouble(latStr);
                                autoLon = Double.parseDouble(lonStr);
                                foundCoords = true;
                            }
                        } catch (Exception e) {
                        }
                    }

                    // 2. Decide: Use Coords OR Geocode
                    if (foundCoords) {
                        latitude = autoLat;
                        longitude = autoLon;
                    } else if (useGeocoding) {
                        if (addrIdx >= cols.length)
                            continue;
                        String rawAddress = cols[addrIdx].replace("\"", "").trim();
                        if (rawAddress.contains("/")) {
                            rawAddress = rawAddress.split("/")[0].trim();
                        }
                        String cleanAddress = rawAddress.replaceAll("\\(.*?\\)", "").trim();

                        double[] coords = geocode(cleanAddress);
                        if (coords == null) {
                            if (failCount < 10)
                                log.warn("Geocoding failed for: [{}]", cleanAddress);
                            failCount++;
                            continue;
                        }
                        longitude = coords[0];
                        latitude = coords[1];
                    } else {
                        // Fallback for non-geocoding mode if no coords found
                        // (Should be covered by foundCoords if logic is correct, but safe check)
                        if (latIdx >= cols.length || lonIdx >= cols.length)
                            continue;

                        // If we are here, it means we expected fixed indices but didn't find good
                        // values
                        // or we were reliant on auto-detect and failed.
                        failCount++;
                        continue;
                    }

                    // Weight logic (Refined as per user request)
                    double weight = 1.0;
                    if (type == RiskType.CCTV)
                        weight = 0.7; // User requested 0.7
                    if (type == RiskType.POLICE)
                        weight = 1.0; // User requested 1.0
                    if (type == RiskType.STREET_LIGHT)
                        weight = 0.4; // User requested 0.4 ("Garodeung")

                    points.add(new RiskPoint(latitude, longitude, weight, type));
                    successCount++;

                    // Batch insert every 1000
                    if (points.size() >= 1000) {
                        riskPointRepository.saveAll(points);
                        points.clear();
                    }

                } catch (Exception e) {
                    failCount++;
                }
            }

            // Save remaining
            if (!points.isEmpty()) {
                riskPointRepository.saveAll(points);
            }

            log.info("Imported {} : Success={}, Fail={}", type, successCount, failCount);

        } catch (Exception e) {
            log.error("Failed to read file: {}", resourcePath, e);
            throw new RuntimeException("Import failed", e);
        }
    }

    private double[] geocode(String address) {
        if (address == null || address.isEmpty())
            return null;

        // Clean address: remove text in parentheses and extra spaces
        String cleanAddress = address.replaceAll("\\(.*?\\)", "").trim();

        try {
            // Rate limiting to avoid API rejection
            Thread.sleep(100);

            String encodedAddr = URLEncoder.encode(cleanAddress, StandardCharsets.UTF_8);
            String apiUrl = "https://api.vworld.kr/req/address?service=address&request=getcoord&version=2.0&crs=epsg:4326&address="
                    + encodedAddr + "&refine=true&simple=false&format=json&type=PARCEL&key="
                    + gisConfig.getVworld().getKey();

            URL url = URI.create(apiUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null)
                    response.append(line);
                br.close();

                JsonNode root = objectMapper.readTree(response.toString());
                JsonNode responseNode = root.path("response");
                if ("OK".equals(responseNode.path("status").asText())) {
                    JsonNode point = responseNode.path("result").path("point");
                    double x = Double.parseDouble(point.path("x").asText()); // Longitude
                    double y = Double.parseDouble(point.path("y").asText()); // Latitude
                    return new double[] { x, y };
                }
            }
        } catch (Exception e) {
            // Ignore individual geocode failures, just return null
        }
        return null;
    }
}
