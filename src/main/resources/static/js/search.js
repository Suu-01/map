// ============================
// 검색 및 지도 상호작용 로직
// ============================

/**
 * 특정 좌표에 마커를 추가하는 함수
 */
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

// 1. 지도 클릭 시 마커 생성 및 실제 주소 정보 표시
if (map) {
    map.on('click', function (evt) {
        const coord = ol.proj.toLonLat(evt.coordinate);
        const lon = coord[0];
        const lat = coord[1];

        // 좌표에 마커 추가
        addMarker(lon, lat);

        // 팝업 오버레이 위치 설정 및 로딩 표시
        overlay.setPosition(evt.coordinate);
        content.innerHTML = `
            <p style="margin: 0; font-size: 13px; color: #666;">정보를 불러오는 중...</p>
        `;

        // 백엔드 중계 API 호출하여 주소 정보 획득 (VWorld Reverse Geocoding)
        const apiUrl = `/api/proxy/address?lon=${lon}&lat=${lat}`;

        fetch(apiUrl)
            .then(res => res.json())
            .then(data => {
                if (data.status === 'OK' || (data.response && data.response.status === 'OK')) {
                    const result = (data.response ? data.response.result[0] : data.result[0]);
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
                console.error('주소 변환 에러:', err);
                content.innerHTML = `<b>⚠️ 오류 발생</b><br/>서버 통신에 실패했습니다.`;
            });
    });
}

// 2. 주소 및 장소 검색 기능
const searchInput = document.getElementById('search-input');
const searchButton = document.getElementById('search-button');

/**
 * 사용자가 입력한 키워드로 주소나 장소를 검색하는 함수
 */
function performSearch() {
    if (!searchInput) return;
    const query = searchInput.value.trim();
    if (!query) {
        alert('검색어를 입력하세요.');
        return;
    }

    // 백엔드 통합 검색 API 호출
    fetch(`/api/proxy/search?address=${encodeURIComponent(query)}`)
        .then(res => res.json())
        .then(data => {
            if (data.response && data.response.status === 'OK' && data.response.result && data.response.result.items.length > 0) {
                const items = data.response.result.items;

                // 최적의 결과 찾기 (띄어쓰기 무시 및 가중치 계산)
                let bestItem = items[0];
                let maxScore = -1;
                const cleanQuery = query.replace(/\s/g, ''); // 검색어 공백 제거

                items.forEach(item => {
                    const originalTitle = item.title.replace(/<[^>]*>?/gm, '').trim(); // HTML 태그 제거
                    const noSpaceTitle = originalTitle.replace(/\s/g, ''); // 제목 공백 제거
                    let score = 0;

                    // 점수 계산 방식
                    if (noSpaceTitle === cleanQuery) {
                        score += 100; // 완전 일치
                    } else if (noSpaceTitle.includes(cleanQuery) || cleanQuery.includes(noSpaceTitle)) {
                        score += 50; // 부분 일치
                    }

                    // 명칭이 짧을수록 우선순위 상승
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

                // 마커 및 팝업 내용 준비
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

                // 지도 이동 애니메이션 실행
                map.getView().cancelAnimations();
                map.getView().animate({
                    center: coordinate,
                    zoom: 17,
                    duration: 800,
                    easing: ol.easing.easeOut
                }, function (complete) {
                    if (complete) {
                        // 이동 완료 후 팝업 표시
                        overlay.setPosition(coordinate);
                    }
                });
            } else {
                const status = data.response ? data.response.status : 'UNKNOWN';
                alert(`검색 결과를 찾을 수 없습니다.\n정확한 주소나 장소명을 입력해 보세요.`);
            }
        })
        .catch(err => {
            console.error('검색 수행 에러:', err);
            alert('검색 도중 서버 오류가 발생했습니다.');
        });
}

// 검색 클릭 및 엔터 키 바인딩
if (searchButton) {
    searchButton.addEventListener('click', performSearch);
}
if (searchInput) {
    searchInput.addEventListener('keypress', function (e) {
        if (e.key === 'Enter') {
            performSearch();
        }
    });
}
