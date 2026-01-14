package com.du.gis_project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * application.yml에 정의된 'gis' 설정을 읽어오는 클래스입니다.
 */
@Configuration
@ConfigurationProperties(prefix = "gis")
public class GisConfig {

    private Vworld vworld = new Vworld();
    private Map map = new Map();

    public Vworld getVworld() {
        return vworld;
    }

    public void setVworld(Vworld vworld) {
        this.vworld = vworld;
    }

    public Map getMap() {
        return map;
    }

    public void setMap(Map map) {
        this.map = map;
    }

    public static class Vworld {
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class Map {
        private Center center = new Center();
        private Bounds bounds = new Bounds();
        private Grid grid = new Grid();

        public Center getCenter() {
            return center;
        }

        public void setCenter(Center center) {
            this.center = center;
        }

        public Bounds getBounds() {
            return bounds;
        }

        public void setBounds(Bounds bounds) {
            this.bounds = bounds;
        }

        public Grid getGrid() {
            return grid;
        }

        public void setGrid(Grid grid) {
            this.grid = grid;
        }

        public static class Center {
            private double lon;
            private double lat;

            public double getLon() {
                return lon;
            }

            public void setLon(double lon) {
                this.lon = lon;
            }

            public double getLat() {
                return lat;
            }

            public void setLat(double lat) {
                this.lat = lat;
            }
        }

        public static class Bounds {
            private double minLat;
            private double maxLat;
            private double minLon;
            private double maxLon;

            public double getMinLat() {
                return minLat;
            }

            public void setMinLat(double minLat) {
                this.minLat = minLat;
            }

            public double getMaxLat() {
                return maxLat;
            }

            public void setMaxLat(double maxLat) {
                this.maxLat = maxLat;
            }

            public double getMinLon() {
                return minLon;
            }

            public void setMinLon(double minLon) {
                this.minLon = minLon;
            }

            public double getMaxLon() {
                return maxLon;
            }

            public void setMaxLon(double maxLon) {
                this.maxLon = maxLon;
            }
        }

        public static class Grid {
            private double stepLat;
            private double stepLon;

            public double getStepLat() {
                return stepLat;
            }

            public void setStepLat(double stepLat) {
                this.stepLat = stepLat;
            }

            public double getStepLon() {
                return stepLon;
            }

            public void setStepLon(double stepLon) {
                this.stepLon = stepLon;
            }
        }
    }
}
