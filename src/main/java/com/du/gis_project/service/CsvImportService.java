package com.du.gis_project.service;

import com.du.gis_project.domain.entity.PopulationPoint;
import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.domain.entity.RiskType;
import com.du.gis_project.repository.PopulationPointRepository;
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
import java.util.Map;
import java.util.HashMap;
import org.springframework.core.io.ClassPathResource;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private final RiskPointRepository riskPointRepository;
    private final PopulationPointRepository populationPointRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String VWORLD_KEY = "CF0C7D65-44C0-31CD-A6FF-80C2E693894A";

    public CsvImportService(RiskPointRepository riskPointRepository,
            PopulationPointRepository populationPointRepository) {
        this.riskPointRepository = riskPointRepository;
        this.populationPointRepository = populationPointRepository;
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

        // 4. Population (UTF-8)
        importPopulation();

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

    @Transactional
    public void importPopulation() {
        log.info("Importing Population data...");
        populationPointRepository.deleteAll();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ClassPathResource("static/data/population.csv").getInputStream(),
                        StandardCharsets.UTF_8))) {

            String line;
            br.readLine(); // Header
            List<PopulationPoint> points = new ArrayList<>();

            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length < 3)
                    continue;

                String district = cols[0].trim();
                String dong = cols[1].trim();
                int count = Integer.parseInt(cols[2].replaceAll("[^0-9]", ""));

                // Get approximate center of the Dong
                double[] coords = getDongCenter(dong);
                if (coords == null) {
                    // Try geocoding if manual mapping is missing
                    coords = geocode("성남시 " + district + " " + dong);
                    if (coords != null) {
                        log.debug("Geocoded {} to {},{}", dong, coords[0], coords[1]);
                    }
                }

                if (coords != null) {
                    points.add(new PopulationPoint(district, dong, count, coords[0], coords[1]));
                }
            }
            populationPointRepository.saveAll(points);
            log.info("Imported {} population points", points.size());

        } catch (Exception e) {
            log.error("Population import failed", e);
        }
    }

    private double[] getDongCenter(String dong) {
        Map<String, double[]> centers = new HashMap<>();
        // Sujeong-gu
        centers.put("신흥1동", new double[] { 37.441, 127.140 });
        centers.put("신흥2동", new double[] { 37.446, 127.146 });
        centers.put("신흥3동", new double[] { 37.438, 127.144 });
        centers.put("태평1동", new double[] { 37.439, 127.126 });
        centers.put("태평2동", new double[] { 37.443, 127.129 });
        centers.put("태평3동", new double[] { 37.440, 127.132 });
        centers.put("태평4동", new double[] { 37.445, 127.133 });
        centers.put("수진1동", new double[] { 37.436, 127.131 });
        centers.put("수진2동", new double[] { 37.438, 127.124 });
        centers.put("단대동", new double[] { 37.452, 127.158 });
        centers.put("산성동", new double[] { 37.456, 127.150 });
        centers.put("양지동", new double[] { 37.452, 127.165 });
        centers.put("복정동", new double[] { 37.456, 127.127 });
        centers.put("고등동", new double[] { 37.429, 127.103 });
        centers.put("신촌동", new double[] { 37.433, 127.098 });

        // Jungwon-gu
        centers.put("성남동", new double[] { 37.436, 127.142 });
        centers.put("중앙동", new double[] { 37.442, 127.152 });
        centers.put("금광1동", new double[] { 37.446, 127.162 });
        centers.put("금광2동", new double[] { 37.450, 127.168 });
        centers.put("은행1동", new double[] { 37.454, 127.164 });
        centers.put("은행2동", new double[] { 37.458, 127.169 });
        centers.put("상대원1동", new double[] { 37.439, 127.172 });
        centers.put("상대원2동", new double[] { 37.435, 127.165 });
        centers.put("상대원3동", new double[] { 37.431, 127.176 });
        centers.put("하대원동", new double[] { 37.428, 127.153 });
        centers.put("도촌동", new double[] { 37.422, 127.162 });

        // Bundang-gu
        centers.put("분당동", new double[] { 37.368, 127.135 });
        centers.put("수내1동", new double[] { 37.378, 127.113 });
        centers.put("수내2동", new double[] { 37.374, 127.119 });
        centers.put("수내3동", new double[] { 37.366, 127.124 });
        centers.put("정자1동", new double[] { 37.365, 127.106 });
        centers.put("정자2동", new double[] { 37.358, 127.115 });
        centers.put("정자3동", new double[] { 37.352, 127.112 });
        centers.put("서현1동", new double[] { 37.388, 127.132 });
        centers.put("서현2동", new double[] { 37.381, 127.140 });
        centers.put("이매1동", new double[] { 37.397, 127.127 });
        centers.put("이매2동", new double[] { 37.404, 127.120 });
        centers.put("야탑1동", new double[] { 37.408, 127.130 });
        centers.put("야탑2동", new double[] { 37.411, 127.122 });
        centers.put("야탑3동", new double[] { 37.418, 127.142 });
        centers.put("판교동", new double[] { 37.391, 127.086 });
        centers.put("삼평동", new double[] { 37.401, 127.111 });
        centers.put("백현동", new double[] { 37.387, 127.107 });
        centers.put("운중동", new double[] { 37.392, 127.054 });

        for (String key : centers.keySet()) {
            if (dong.trim().contains(key))
                return centers.get(key);
        }

        return null;
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
                    + encodedAddr + "&refine=true&simple=false&format=json&type=PARCEL&key=" + VWORLD_KEY;

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
