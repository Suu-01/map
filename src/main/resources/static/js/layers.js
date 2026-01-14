// ============================
// 안전 시설 레이어 관리 (Vector 방식)
// ============================

// 1. 데이터 소스 정의 (데이터가 담길 통)
const cctvSource = new ol.source.Vector();
const policeSource = new ol.source.Vector();
const lightSource = new ol.source.Vector();

// 2. 레이어 정의 (지도에 그려질 스타일 및 설정)
// CCTV: 빨간색 원
const cctvLayer = new ol.layer.Vector({
    source: cctvSource,
    visible: false,
    style: new ol.style.Style({
        image: new ol.style.Circle({
            radius: 5,
            fill: new ol.style.Fill({ color: 'rgba(231, 76, 60, 0.8)' }), // 빨간색
            stroke: new ol.style.Stroke({ color: 'white', width: 2 })
        })
    }),
    zIndex: 10
});

// 경찰 시설: 파란색 원
const policeLayer = new ol.layer.Vector({
    source: policeSource,
    visible: false,
    style: new ol.style.Style({
        image: new ol.style.Circle({
            radius: 6,
            fill: new ol.style.Fill({ color: 'rgba(41, 128, 185, 0.9)' }), // 파란색
            stroke: new ol.style.Stroke({ color: 'white', width: 2 })
        })
    }),
    zIndex: 11
});

// 가로등: 노란색 작은 원
const lightLayer = new ol.layer.Vector({
    source: lightSource,
    visible: false,
    style: new ol.style.Style({
        image: new ol.style.Circle({
            radius: 3,
            fill: new ol.style.Fill({ color: 'rgba(241, 196, 15, 0.8)' }), // 노란색
            stroke: new ol.style.Stroke({ color: '#333', width: 1 })
        })
    }),
    zIndex: 9
});

// 지도에 레이어 추가
if (map) {
    map.addLayer(cctvLayer);
    map.addLayer(policeLayer);
    map.addLayer(lightLayer);
}

// 3. 데이터 로드 및 이벤트 리스너 함수
/**
 * 서버에서 특정 타입의 안전 시설 데이터를 가져와 소스에 추가하는 함수
 */
function loadData(type, source) {
    // 이미 데이터를 가져왔다면 다시 요청하지 않음
    if (source.getFeatures().length > 0) return;

    const url = `/api/risks?type=${type}`;
    fetch(url).then(res => res.json()).then(data => {
        if (data.status === 'OK') {
            const features = data.result.map(p => new ol.Feature({
                geometry: new ol.geom.Point(ol.proj.fromLonLat([p.longitude, p.latitude])),
                weight: p.weight,
                type: p.type
            }));
            source.addFeatures(features);
        }
    });
}

/**
 * 체크박스 상태에 따라 레이어를 켜고 끄는 이벤트 설정 함수
 */
function setupLayerListener(id, layer, type) {
    const checkbox = document.getElementById(id);
    if (checkbox) {
        checkbox.addEventListener('change', function () {
            if (this.checked) {
                loadData(type, layer.getSource());
                layer.setVisible(true);
            } else {
                layer.setVisible(false);
            }
        });
    }
}

// 레이어별 리스너 초기화
setupLayerListener('chk-cctv', cctvLayer, 'CCTV');
setupLayerListener('chk-police', policeLayer, 'POLICE');
setupLayerListener('chk-light', lightLayer, 'STREET_LIGHT');
