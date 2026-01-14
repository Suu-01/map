package com.du.gis_project.util;

/**
 * 지도 관련 수학적 계산을 처리하는 유틸리티 클래스
 */
public class DistanceUtil {

    private static final double EARTH_RADIUS = 6371000; // 지구 반지름 (미터)

    /**
     * 하버사인(Haversine) 공식을 이용한 두 지점 사이의 실제 거리(미터) 계산
     * 
     * @param lat1 지점1 위도
     * @param lon1 지점1 경도
     * @param lat2 지점2 위도
     * @param lon2 지점2 경도
     * @return 두 지점 사이의 거리 (미터)
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
