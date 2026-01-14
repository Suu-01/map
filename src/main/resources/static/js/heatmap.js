// ============================
// 위험도 히트맵 레이어 관리
// ============================

// 1. 데이터 소스 정의
const heatmapSource = new ol.source.Vector(); // 통합 위험도 그리드 데이터용
const refinedRiskSource = new ol.source.Vector(); // 정밀 분석 히트맵 데이터용

// 2. 히트맵 레이어 생성 및 설정
let heatmapLayer;
try {
    if (typeof ol.layer.Heatmap !== 'undefined') {
        heatmapLayer = new ol.layer.Heatmap({
            source: heatmapSource,
            blur: 45,    // 초고밀도 그리드를 위한 부드러운 보정값
            radius: 35,  // 그리드 점들이 하나의 면으로 보이게 만드는 큰 반경
            weight: function (feature) {
                const score = feature.get('weight') || 0;
                // 가중치를 3.0점 만점 기준으로 정규화 (전체적인 색감 밸런스 유지)
                return Math.max(0.1, Math.min(score / 3.0, 1.0));
            },
            // 열화상 그라디언트: 진한 파랑(안전) -> 빨강(위험)
            gradient: ['#0000ff', '#00ffff', '#00ff00', '#ffff00', '#ff0000'],
            visible: false,
            opacity: 0.6, // 밑지도의 도로와 건물이 비쳐 보이도록 투명도 설정
            zIndex: 5
        });
        if (map) map.addLayer(heatmapLayer);
    }
} catch (e) {
    console.error("통합 히트맵 초기화 에러:", e);
}

let refinedRiskLayer;
try {
    if (typeof ol.layer.Heatmap !== 'undefined') {
        refinedRiskLayer = new ol.layer.Heatmap({
            source: refinedRiskSource,
            blur: 50,    // 정밀 맵을 위한 더 부드러운 효과
            radius: 40,  // 시설 주변 '안전 버블'을 위한 약간 더 큰 반경
            weight: function (feature) {
                const score = feature.get('weight') || 0;
                // 정밀 분석 히트맵도 3.0점 만점 기준으로 색상 매핑
                return Math.max(0.1, Math.min(score / 3.0, 1.0));
            },
            // 정밀 히트맵용 열화상 색상 체계
            gradient: ['#0000ff', '#00ffff', '#00ff00', '#ffff00', '#ff0000'],
            visible: false,
            opacity: 0.6, // 시인성 확보를 위한 반투명 설정
            zIndex: 6
        });
        if (map) map.addLayer(refinedRiskLayer);
    }
} catch (e) {
    console.error("정밀 히트맵 초기화 에러:", e);
}

// 3. 체크박스 이벤트 리스너
// [통합 위험도 히트맵] 토글
const chkHeatmap = document.getElementById('chk-heatmap');
if (chkHeatmap) {
    chkHeatmap.addEventListener('change', function () {
        if (this.checked && heatmapLayer) {
            // 데이터가 없으면 서버에서 가져옴
            if (heatmapSource.getFeatures().length === 0) {
                const url = '/api/risks/blind-spots';
                console.log("통합 위험도 데이터를 불러오는 중...");
                fetch(url)
                    .then(res => res.json())
                    .then(data => {
                        if (data.status === 'OK') {
                            const features = data.result.map(p => new ol.Feature({
                                geometry: new ol.geom.Point(ol.proj.fromLonLat([p.lon, p.lat])),
                                weight: p.score
                            }));
                            heatmapSource.addFeatures(features);
                        }
                    });
            }
            heatmapLayer.setVisible(true);
        } else if (heatmapLayer) {
            heatmapLayer.setVisible(false);
        }
    });
}

// [위험도 히트맵(정밀)] 토글
const chkRefinedRisk = document.getElementById('chk-refined-risk');
if (chkRefinedRisk) {
    chkRefinedRisk.addEventListener('change', function () {
        if (this.checked && refinedRiskLayer) {
            // 데이터가 없으면 서버에서 가져옴
            if (refinedRiskSource.getFeatures().length === 0) {
                const url = '/api/risks/refined-risk';
                fetch(url)
                    .then(res => res.json())
                    .then(data => {
                        if (data.status === 'OK') {
                            const features = data.result.map(p => new ol.Feature({
                                geometry: new ol.geom.Point(ol.proj.fromLonLat([p.lon, p.lat])),
                                weight: p.score
                            }));
                            refinedRiskSource.addFeatures(features);
                        }
                    })
                    .catch(err => {
                        console.error("정밀 위험도 데이터 수신 에러:", err);
                    });
            }
            refinedRiskLayer.setVisible(true);
        } else if (refinedRiskLayer) {
            refinedRiskLayer.setVisible(false);
        }
    });
}

// 4. 줌 연동 동적 반경 조절 (확대 시 색 빠짐 및 화면 가득 참 방지)
/**
 * 지도의 줌 레벨에 따라 히트맵의 반경(Radius)과 번짐(Blur) 효과를 유동적으로 변경
 */
function updateHeatmapRadius() {
    if (!map) return;
    const zoom = map.getView().getZoom();

    // 확대할수록 반경이 커짐 (선형적인 보간법 적용)
    // 줌 13: 25px(전체), 줌 15: 40px, 줌 17: 56px 정도의 밸런스 유지
    const newRadius = Math.max(25, (zoom - 10) * 8);
    const newBlur = newRadius * 1.5; // 자연스러운 그라디언트를 위해 반경보다 크게 설정

    if (heatmapLayer) {
        heatmapLayer.setRadius(newRadius);
        heatmapLayer.setBlur(newBlur);
    }
    if (refinedRiskLayer) {
        refinedRiskLayer.setRadius(newRadius);
        refinedRiskLayer.setBlur(newBlur);
    }
}

// 지도의 해상도가 바뀔 때(줌 인/아웃)마다 반경 업데이트
if (map) {
    map.getView().on('change:resolution', updateHeatmapRadius);
    updateHeatmapRadius(); // 초기 실행
}
