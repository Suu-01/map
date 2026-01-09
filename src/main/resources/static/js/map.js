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
        markerSource.clear(); // 팝업 닫을 때 마커도 함께 제거 (동기화)
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
        center: ol.proj.fromLonLat([127.138868, 37.419720]), // 성남시청
        zoom: 13
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

// ============================
// 6. 마커 추가 함수
// ============================
function addMarker(lon, lat) {
    // 기존 마커 삭제 (항상 최신 핑만 유지)
    markerSource.clear();

    const marker = new ol.Feature({
        geometry: new ol.geom.Point(
            ol.proj.fromLonLat([lon, lat])
        )
    });

    marker.setStyle(iconStyle);
    markerSource.addFeature(marker);
}

// ============================
// 7. 지도 클릭 시 마커 생성 및 실제 주소 정보 표시
// ============================
map.on('click', function (evt) {
    const coord = ol.proj.toLonLat(evt.coordinate);
    const lon = coord[0];
    const lat = coord[1];

    // 1. 마커 추가
    addMarker(lon, lat);

    // 2. 팝업 초기화 및 로딩 표시
    overlay.setPosition(evt.coordinate);
    content.innerHTML = `
        <p style="margin: 0; font-size: 13px; color: #666;">정보를 불러오는 중...</p>
    `;

    // 3. 백엔드 중계 API 호출 (CORS 해결을 위해 로컬 서버 이용)
    const apiUrl = `/api/proxy/address?lon=${lon}&lat=${lat}`;

    fetch(apiUrl)
        .then(res => res.json())
        .then(data => {
            if (data.response && data.response.status === 'OK') {
                const result = data.response.result[0];
                const address = result.text;
                const type = result.type === 'parcel' ? '지번 주소' : '도로명 주소';

                content.innerHTML = `
                    <span style="font-weight: bold; color: #2ecc71;">[${type}]</span><br/>
                    ${address}<br/>
                    <p style="margin-top: 8px; font-size: 11px; color: #999; margin-bottom: 0;">
                        좌표: ${lon.toFixed(5)}, ${lat.toFixed(5)}
                    </p>
                `;
            } else {
                content.innerHTML = `
                    주소 정보를 찾을 수 없는 지역입니다.<br/>
                    <p style="margin-top: 8px; font-size: 11px; color: #999; margin-bottom: 0;">
                        좌표: ${lon.toFixed(5)}, ${lat.toFixed(5)}
                    </p>
                `;
            }
        })
        .catch(err => {
            console.error('API 호출 에러:', err);
            content.innerHTML = `<b>⚠️ 오류 발생</b><br/>서버 통신에 실패했습니다.`;
        });

    console.log('클릭 좌표:', coord);
});

// ============================
// 8. 주소 검색 기능
// ============================
const searchInput = document.getElementById('search-input');
const searchButton = document.getElementById('search-button');

function performSearch() {
    if (!searchInput) return;
    const query = searchInput.value.trim();
    if (!query) {
        alert('검색어를 입력하세요.');
        return;
    }

    // 백엔드 통합 검색 프록시 호출
    fetch(`/api/proxy/search?address=${encodeURIComponent(query)}`)
        .then(res => res.json())
        .then(data => {
            if (data.response && data.response.status === 'OK' && data.response.result && data.response.result.items.length > 0) {
                const items = data.response.result.items;

                // 1. 최적의 결과 찾기 (지능형 랭킹 시스템 - 띄어쓰기 무시 버전)
                let bestItem = items[0];
                let maxScore = -1;
                const cleanQuery = query.replace(/\s/g, ''); // 검색어 공백 제거

                items.forEach(item => {
                    const originalTitle = item.title.replace(/<[^>]*>?/gm, '').trim(); // HTML 태그 제거
                    const noSpaceTitle = originalTitle.replace(/\s/g, ''); // 제목 공백 제거
                    let score = 0;

                    // 점수 계산 로직 (공백 없는 텍스트 기준)
                    if (noSpaceTitle === cleanQuery) {
                        score += 100; // 완전 일치
                    } else if (noSpaceTitle.includes(cleanQuery) || cleanQuery.includes(noSpaceTitle)) {
                        score += 50; // 부분 일치
                    }

                    // 명칭이 짧을수록(대표 지명일 확률이 높음) 가산점
                    score += (100 - noSpaceTitle.length);

                    if (score > maxScore) {
                        maxScore = score;
                        bestItem = item;
                    }
                });

                const item = bestItem;
                const lon = parseFloat(item.point.x);
                const lat = parseFloat(item.point.y);
                const coordinate = ol.proj.fromLonLat([lon, lat]);

                // 1. 마커 및 팝업 내용 미리 준비
                markerSource.clear();
                addMarker(lon, lat);

                content.innerHTML = `
                    <span style="font-weight: bold; color: #3498db;">[검색 결과]</span><br/>
                    <span style="font-weight: bold;">${item.title}</span><br/>
                    <span style="font-size: 12px; color: #555;">${item.address.road || item.address.parcel || ''}</span>
                    <p style="margin-top: 8px; font-size: 11px; color: #999; margin-bottom: 0;">
                        좌표: ${lon.toFixed(5)}, ${lat.toFixed(5)}
                    </p>
                `;

                // 2. 이전 애니메이션이 있다면 취소 (튕김 방지)
                map.getView().cancelAnimations();

                // 3. 지도 이동 (애니메이션이 완료된 후에 팝업을 표시해야 튕기지 않음)
                map.getView().animate({
                    center: coordinate,
                    zoom: 17,
                    duration: 800,
                    easing: ol.easing.easeOut
                }, function (complete) {
                    if (complete) {
                        // 이동이 완전히 끝난 후 팝업 좌표 설정 (autoPan 충돌 방지 핵심)
                        overlay.setPosition(coordinate);
                    }
                });
            } else {
                const status = data.response ? data.response.status : 'UNKNOWN';
                const errorMsg = data.response ? data.response.message : '';
                alert(`검색 결과를 찾을 수 없습니다.\n(상태: ${status}${errorMsg ? ', 사유: ' + errorMsg : ''})\n정확한 주소나 장소명을 입력해보세요.`);
            }
        })
        .catch(err => {
            console.error('검색 에러:', err);
            alert('검색 도중 서버 오류가 발생했습니다.');
        });
}

// 클릭 이벤트
if (searchButton) {
    searchButton.addEventListener('click', performSearch);
}

// 엔터 키 이벤트
if (searchInput) {
    searchInput.addEventListener('keypress', function (e) {
        if (e.key === 'Enter') {
            performSearch();
        }
    });
}

// ============================
// 9. Risk Layer Management (New)
// ============================
(function () {
    // 1. Define Sources
    const cctvSource = new ol.source.Vector();
    const policeSource = new ol.source.Vector();
    const lightSource = new ol.source.Vector();
    const heatmapSource = new ol.source.Vector(); // Integrated Risk Grid
    const refinedRiskSource = new ol.source.Vector(); // Refined Risk Heatmap (New)

    // 2. Define Layers
    // CCTV: Red Circle
    const cctvLayer = new ol.layer.Vector({
        source: cctvSource,
        visible: false,
        style: new ol.style.Style({
            image: new ol.style.Circle({
                radius: 5,
                fill: new ol.style.Fill({ color: 'rgba(231, 76, 60, 0.8)' }), // Red
                stroke: new ol.style.Stroke({ color: 'white', width: 2 })
            })
        }),
        zIndex: 10
    });

    // Police: Blue Circle (Icon logic can be added later)
    const policeLayer = new ol.layer.Vector({
        source: policeSource,
        visible: false,
        style: new ol.style.Style({
            image: new ol.style.Circle({
                radius: 6,
                fill: new ol.style.Fill({ color: 'rgba(41, 128, 185, 0.9)' }), // Blue
                stroke: new ol.style.Stroke({ color: 'white', width: 2 })
            })
        }),
        zIndex: 11
    });

    // Streetlight: Yellow Circle
    const lightLayer = new ol.layer.Vector({
        source: lightSource,
        visible: false,
        style: new ol.style.Style({
            image: new ol.style.Circle({
                radius: 3,
                fill: new ol.style.Fill({ color: 'rgba(241, 196, 15, 0.8)' }), // Yellow
                stroke: new ol.style.Stroke({ color: '#333', width: 1 })
            })
        }),
        zIndex: 9
    });

    let heatmapLayer;
    try {
        if (typeof ol.layer.Heatmap !== 'undefined') {
            heatmapLayer = new ol.layer.Heatmap({
                source: heatmapSource,
                blur: 45,    // Smoother interpolation for ultra-dense grid
                radius: 35,  // Large radius to ensure grid points merge into a solid background
                weight: function (feature) {
                    const score = feature.get('weight') || 0;
                    // Min weight 0.1 ensures 'Safe' areas are solid Blue, not transparent
                    return Math.max(0.1, Math.min(score / 8.0, 1.0));
                },
                // Thermal Gradient: Solid Blue (Safe) -> Red (Danger)
                gradient: ['#0000ff', '#00ffff', '#00ff00', '#ffff00', '#ff0000'],
                visible: false,
                opacity: 0.6, // Transparent enough to see streets and labels
                zIndex: 5
            });
            map.addLayer(heatmapLayer);
        }
    } catch (e) {
        console.error("Heatmap init error:", e);
    }

    let refinedRiskLayer;
    try {
        if (typeof ol.layer.Heatmap !== 'undefined') {
            refinedRiskLayer = new ol.layer.Heatmap({
                source: refinedRiskSource,
                blur: 50,    // Extra smooth for refined map
                radius: 40,  // Slightly larger radius for 'SAFE bubbles'
                weight: function (feature) {
                    const score = feature.get('weight') || 0;
                    // Min weight 0.1 for solid coverage
                    return Math.max(0.1, Math.min(score / 8.0, 1.0));
                },
                // Thermal Gradient for Refined Map
                gradient: ['#0000ff', '#00ffff', '#00ff00', '#ffff00', '#ff0000'],
                visible: false,
                opacity: 0.6, // Consistent transparency
                zIndex: 6
            });
            map.addLayer(refinedRiskLayer);
        }
    } catch (e) {
        console.error("Refined Heatmap init error:", e);
    }

    // Add Vector Layers to Map (CRITICAL: Restoring these)
    map.addLayer(cctvLayer);
    map.addLayer(policeLayer);
    map.addLayer(lightLayer);

    // Blind Spot Layer (Inverted Risk)
    // Unified Heatmap Listener
    const chkHeatmap = document.getElementById('chk-heatmap');
    if (chkHeatmap) {
        chkHeatmap.addEventListener('change', function () {
            if (this.checked && heatmapLayer) {
                if (heatmapSource.getFeatures().length === 0) {
                    const url = '/api/risks/blind-spots';
                    console.log("Fetching Integrated Risk Data...");
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

    // New: Refined Risk Heatmap Listener
    const chkRefinedRisk = document.getElementById('chk-refined-risk');
    if (chkRefinedRisk) {
        chkRefinedRisk.addEventListener('change', function () {
            if (this.checked && refinedRiskLayer) {
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
                        });
                }
                refinedRiskLayer.setVisible(true);
            } else if (refinedRiskLayer) {
                refinedRiskLayer.setVisible(false);
            }
        });
    }

    // Helper functions
    function loadData(type, source) {
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

    function setupListener(id, layer, type) {
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

    setupListener('chk-cctv', cctvLayer, 'CCTV');
    setupListener('chk-police', policeLayer, 'POLICE');
    setupListener('chk-light', lightLayer, 'STREET_LIGHT');

    // 5. Import Button
    const btnImport = document.getElementById('btn-import-data');
    if (btnImport) {
        btnImport.addEventListener('click', function () {
            if (!confirm('데이터 가져오기를 시작하시겠습니까?\n(기존 데이터는 삭제되며, 시간이 수 분 걸릴 수 있습니다.)')) return;

            this.disabled = true;
            this.innerText = "가져오기 진행 중... (서버 로그 확인)";

            fetch('/api/import', { method: 'POST' })
                .then(res => res.json())
                .then(data => {
                    // Force clear sources so they re-fetch on next toggle
                    cctvSource.clear();
                    policeSource.clear();
                    lightSource.clear();
                    heatmapSource.clear();
                    refinedRiskSource.clear();

                    alert('데이터 가져오기 시작됨!\n\n완료까지 1~2분 정도 걸릴 수 있습니다.\n잠시 후 체크박스를 다시 켜면 최신 데이터가 반영됩니다.');

                    setTimeout(() => {
                        this.disabled = false;
                        this.innerText = "🔄 데이터 가져오기 (관리자용)";
                    }, 3000);
                })
                .catch(err => {
                    alert('요청 실패: ' + err);
                    this.disabled = false;
                });
        });
    }

    // 6. Dynamic Heatmap Radius (Fixed zoom-fading and over-bloating problem)
    function updateHeatmapRadius() {
        const zoom = map.getView().getZoom();
        // Softer scaling: Zoom 13: 35px, Zoom 15: 55px, Zoom 17: 85px
        // Prevents the "blanket" effect that covers the whole map while staying connected
        const newRadius = Math.max(25, (zoom - 10) * 8);
        const newBlur = newRadius * 1.5; // Slightly more blur for smoother transition

        if (heatmapLayer) {
            heatmapLayer.setRadius(newRadius);
            heatmapLayer.setBlur(newBlur);
        }
        if (refinedRiskLayer) {
            refinedRiskLayer.setRadius(newRadius);
            refinedRiskLayer.setBlur(newBlur);
        }
    }

    map.getView().on('change:resolution', updateHeatmapRadius);
    updateHeatmapRadius(); // Initial call

    // 7. Admin Section Toggle
    const toggleAdmin = document.getElementById('toggle-admin');
    const adminSection = document.getElementById('admin-section');
    if (toggleAdmin && adminSection) {
        toggleAdmin.addEventListener('click', function () {
            const isHidden = adminSection.style.display === 'none';
            adminSection.style.display = isHidden ? 'block' : 'none';
            this.classList.toggle('active', !isHidden);
        });
    }

})();
