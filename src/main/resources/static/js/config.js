/**
 * GIS 프로젝트 전역 설정 관리
 */
const MapConfig = {
    // 기본 설정 (서버 연결 실패 시 대비)
    VWORLD_KEY: 'CF0C7D65-44C0-31CD-A6FF-80C2E693894A',
    CENTER: [127.138868, 37.419720], // 성남시청
    ZOOM: 13,

    /**
     * 서버로부터 최신 설정을 로드합니다. (보안 및 동적 설정을 위함)
     */
    async fetchConfig() {
        try {
            const res = await fetch('/api/config');
            const data = await res.json();
            if (data && data.vworld) {
                this.VWORLD_KEY = data.vworld.key;
                this.CENTER = [data.map.center.lon, data.map.center.lat];
                console.log('서버 설정 로드 완료:', this.VWORLD_KEY);
            }
        } catch (e) {
            console.warn('서버 설정 로드 실패 (기본값 사용):', e);
        }
    }
};

// 초기화 시 서버 설정 로드 실행
MapConfig.fetchConfig();
