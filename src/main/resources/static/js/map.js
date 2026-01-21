// ============================
// 1. VWorld 타일 레이어 (Base/Satellite/Hybrid)
// ============================
console.log("Map initializing with VWorld Key:", MapConfig.VWORLD_KEY);

// 기본 배경 (일반 지도)
const baseLayer = new ol.layer.Tile({
    source: new ol.source.XYZ({
        url: `https://api.vworld.kr/req/wmts/1.0.0/${MapConfig.VWORLD_KEY}/Base/{z}/{y}/{x}.png`
    }),
    visible: true,
    zIndex: 0
});

// 위성 배경
const satelliteLayer = new ol.layer.Tile({
    source: new ol.source.XYZ({
        url: `https://api.vworld.kr/req/wmts/1.0.0/${MapConfig.VWORLD_KEY}/Satellite/{z}/{y}/{x}.jpeg`
    }),
    visible: false,
    zIndex: 0
});

// 하이브리드 (영상 위 라벨/도로)
const hybridLayer = new ol.layer.Tile({
    source: new ol.source.XYZ({
        url: `https://api.vworld.kr/req/wmts/1.0.0/${MapConfig.VWORLD_KEY}/Hybrid/{z}/{y}/{x}.png`
    }),
    visible: false,
    zIndex: 1
});

// ============================
// 2. 마커 Source / Layer (검색용)
// ============================
const markerSource = new ol.source.Vector();
const markerLayer = new ol.layer.Vector({
    source: markerSource,
    zIndex: 100
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
        if (markerSource) markerSource.clear();
        closer.blur();
        return false;
    };
}

// ============================
// 4. 지도 생성
// ============================
const map = new ol.Map({
    target: 'map',
    layers: [baseLayer, satelliteLayer, hybridLayer, markerLayer],
    overlays: [overlay],
    view: new ol.View({
        center: ol.proj.fromLonLat(MapConfig.CENTER),
        zoom: MapConfig.ZOOM
    })
});

// ============================
// 5. 맵 타입 전환 로직
// ============================
const btnBase = document.getElementById('btn-base');
const btnSatellite = document.getElementById('btn-satellite');

console.log("Map type buttons found:", !!btnBase, !!btnSatellite);

if (btnBase && btnSatellite) {
    btnBase.onclick = function (e) {
        e.preventDefault();
        console.log("Switching to Base Layer");
        baseLayer.setVisible(true);
        satelliteLayer.setVisible(false);
        hybridLayer.setVisible(false);
        btnBase.classList.add('active');
        btnSatellite.classList.remove('active');
    };

    btnSatellite.onclick = function (e) {
        e.preventDefault();
        console.log("Switching to Satellite Layer");
        baseLayer.setVisible(false);
        satelliteLayer.setVisible(true);
        hybridLayer.setVisible(true);
        btnBase.classList.remove('active');
        btnSatellite.classList.add('active');
    };
}

// 마커 스타일
const iconStyle = new ol.style.Style({
    image: new ol.style.Icon({
        src: '/img/marker.png',
        scale: 0.8,
        anchor: [0.5, 1]
    })
});
