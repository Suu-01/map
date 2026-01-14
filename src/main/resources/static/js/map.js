// ============================
// 1. VWorld 타일 레이어
// ============================
const vworldLayer = new ol.layer.Tile({
    source: new ol.source.XYZ({
        url: `https://api.vworld.kr/req/wmts/1.0.0/${MapConfig.VWORLD_KEY}/Base/{z}/{y}/{x}.png`
    })
});

// ============================
// 2. 마커 Source / Layer (검색용)
// ============================
const markerSource = new ol.source.Vector();

const markerLayer = new ol.layer.Vector({
    source: markerSource
});

// ============================
// 3. 팝업 오버레이 설정
// ============================
const container = document.getElementById('popup');
const content = document.getElementById('popup-content');
const closer = document.getElementById('popup-closer');

const overlay = new ol.Overlay({
    element: container,
    autoPan: true,
    autoPanAnimation: {
        duration: 250
    }
});

if (closer) {
    closer.onclick = function () {
        overlay.setPosition(undefined);
        if (markerSource) markerSource.clear(); // 팝업 닫을 때 마커도 함께 제거 (동기화)
        closer.blur();
        return false;
    };
}

// ============================
// 4. 지도 생성
// ============================
const map = new ol.Map({
    target: 'map',
    layers: [vworldLayer, markerLayer],
    overlays: [overlay], // 오버레이 등록
    view: new ol.View({
        center: ol.proj.fromLonLat(MapConfig.CENTER), // 설정된 중심점 사용
        zoom: MapConfig.ZOOM
    })
});

// ============================
// 5. 마커 스타일
// ============================
const iconStyle = new ol.style.Style({
    image: new ol.style.Icon({
        src: '/img/marker.png',
        scale: 0.8,
        anchor: [0.5, 1]
    })
});
