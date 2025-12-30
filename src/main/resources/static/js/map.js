const VWORLD_KEY = 'CF0C7D65-44C0-31CD-A6FF-80C2E693894A';

// ============================
// 1. VWorld 타일 레이어
// ============================
const vworldLayer = new ol.layer.Tile({
    source: new ol.source.XYZ({
        url: `https://api.vworld.kr/req/wmts/1.0.0/${VWORLD_KEY}/Base/{z}/{y}/{x}.png`
    })
});

// ============================
// 2. 마커 Source / Layer
// ============================
const markerSource = new ol.source.Vector();

const markerLayer = new ol.layer.Vector({
    source: markerSource
});

// ============================
// 3. 지도 생성
// ============================
const map = new ol.Map({
    target: 'map',
    layers: [vworldLayer, markerLayer],
    view: new ol.View({
        center: ol.proj.fromLonLat([126.9780, 37.5665]), // 서울시청
        zoom: 12
    })
});

// ============================
// 4. 마커 스타일
// ============================
const iconStyle = new ol.style.Style({
    image: new ol.style.Icon({
        src: '/img/marker.png',
        scale: 0.8,
        anchor: [0.5, 1]
    })
});

// ============================
// 5. 마커 추가 함수
// ============================
function addMarker(lon, lat) {
    const marker = new ol.Feature({
        geometry: new ol.geom.Point(
            ol.proj.fromLonLat([lon, lat])
        )
    });

    marker.setStyle(iconStyle);
    markerSource.addFeature(marker);
}

// ============================
// 6. 지도 클릭 시 마커 생성
// ============================
map.on('click', function (evt) {
    const coord = ol.proj.toLonLat(evt.coordinate);
    addMarker(coord[0], coord[1]);
    console.log('클릭 좌표:', coord);
});

// ============================
// 7. 서버 좌표 불러오기 (나중에 구현 예정)
// ============================
