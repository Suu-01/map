// ============================
// 검색 및 지도 상호작용 로직 (고정밀 v6 - 정제된 데이터 처리 및 방어 코드)
// ============================

/**
 * 주소 검색 기능 (VWorld Search API 2.0 연동 - 4단계 파이프라인 대응)
 */
async function performSearch() {
    const searchInput = document.getElementById('search-input');
    if (!searchInput) return;

    const query = searchInput.value.trim();
    if (!query) return alert("주소를 입력하세요.");

    try {
        // 검색 시작 시 피드백
        markerSource.clear();
        overlay.setPosition(undefined);

        const response = await fetch(`/api/proxy/search?address=${encodeURIComponent(query)}`);
        const jsonResponse = await response.json();

        if (jsonResponse.status === "ERROR") {
            console.error("서버 오류:", jsonResponse.message);
            alert("검색 중 오류가 발생했습니다: " + jsonResponse.message);
            return;
        }

        const data = JSON.parse(jsonResponse.data);
        const results = data.response.result;

        if (data.response.status === "OK" && results && results.items && results.items.length > 0) {
            const item = results.items[0];
            const x = parseFloat(item.point.x);
            const y = parseFloat(item.point.y);

            // [UI 최적화] 검색 방식에 따른 스마트 타이틀 결정
            const foundType = jsonResponse.foundType; // place, road, parcel, district
            const roadAddr = item.address?.road || "";
            const parcelAddr = item.address?.parcel || "";

            // HTML 태그 제거 및 정리
            let cleanTitle = (item.title || "").replace(/<[^>]*>?/gm, '').trim();

            // 제목 결정 로직 (사용자가 입력한 검색어를 우선적으로 존중)
            if (foundType === 'place' && cleanTitle && cleanTitle !== roadAddr && cleanTitle !== parcelAddr) {
                // 확실한 명칭이 발견된 경우
            } else {
                // 주소 검색 결과인 경우 사용자의 입력값이나 정제된 쿼리를 제목으로 사용
                cleanTitle = jsonResponse.query || query;
            }

            // 1. 지도 이동
            map.getView().animate({
                center: [x, y],
                zoom: 17,
                duration: 900,
                easing: ol.easing.easeOut
            }, (complete) => {
                if (complete) {
                    overlay.setPosition([x, y]);

                    // 팝업 내용 구성
                    let popupHtml = `
                        <div style="min-width: 220px; padding: 5px;">
                            <div style="font-weight: 800; color: #2c3e50; font-size: 16px; margin-bottom: 8px; border-bottom: 1px solid #eee; padding-bottom: 5px;">
                                📍 ${cleanTitle}
                            </div>
                            <div style="font-size: 13px; color: #555; line-height: 1.6;">
                    `;

                    // 도로명 주소 표시
                    if (roadAddr) {
                        popupHtml += `<div style="margin-bottom: 5px; display: flex; align-items: flex-start;">
                            <span style="background: #3498db; color: white; font-size: 10px; padding: 2px 4px; border-radius: 3px; font-weight: bold; margin-right: 6px; white-space: nowrap; margin-top: 2px;">도로명</span>
                            <span>${roadAddr}</span>
                        </div>`;
                    }

                    // 지번 주소 표시
                    if (parcelAddr) {
                        popupHtml += `<div style="display: flex; align-items: flex-start;">
                            <span style="background: #95a5a6; color: white; font-size: 10px; padding: 2px 4px; border-radius: 3px; font-weight: bold; margin-right: 6px; white-space: nowrap; margin-top: 2px;">지번</span>
                            <span>${parcelAddr}</span>
                        </div>`;
                    }

                    // 검색 편의성 정보 추가
                    let typeLabel = "";
                    if (foundType === 'road') typeLabel = "도로명 주소 검색 결과입니다.";
                    else if (foundType === 'parcel') typeLabel = "지번 주소(필지) 검색 결과입니다.";
                    else if (foundType === 'district') typeLabel = "행정 구역 검색 결과입니다.";

                    if (typeLabel) {
                        popupHtml += `<div style="margin-top: 8px; font-size: 11px; color: #e67e22; border-top: 1px dashed #ddd; padding-top: 5px;">* ${typeLabel}</div>`;
                    }

                    popupHtml += `</div></div>`;
                    content.innerHTML = popupHtml;
                }
            });

            // 2. 마커 표시
            const feature = new ol.Feature({
                geometry: new ol.geom.Point([x, y])
            });
            feature.setStyle(iconStyle);
            markerSource.addFeature(feature);

        } else {
            alert("검색 결과가 없습니다. 도로명 주소나 명칭을 정확히 입력해 주세요.");
        }
    } catch (e) {
        console.error("검색 중 오류 발생:", e);
        alert("검색 처리 중 오류가 발생했습니다.");
    }
}

/**
 * 지도를 클릭했을 때 상세 주소 정보를 가져오는 함수 (Reverse Geocoding)
 */
if (map) {
    map.on('click', async function (evt) {
        const coord = ol.proj.toLonLat(evt.coordinate);
        const lon = coord[0];
        const lat = coord[1];

        markerSource.clear();
        overlay.setPosition(undefined);

        const marker = new ol.Feature({
            geometry: new ol.geom.Point(evt.coordinate)
        });
        marker.setStyle(iconStyle);
        markerSource.addFeature(marker);

        try {
            const response = await fetch(`/api/proxy/address?lon=${lon}&lat=${lat}`);
            const jsonResponse = await response.json();

            if (jsonResponse.status === "OK") {
                const data = JSON.parse(jsonResponse.data);
                let results = data.response.result;

                if (data.response.status === "OK" && results) {
                    overlay.setPosition(evt.coordinate);

                    // 결과를 무조건 배열로 처리 (VWorld가 단일 객체로 줄 가능성 대비)
                    if (!Array.isArray(results)) {
                        results = [results];
                    }

                    if (results.length > 0) {
                        let popupHtml = `
                            <div style="min-width: 200px; padding: 5px;">
                                <div style="font-weight: 800; color: #2c3e50; font-size: 14px; margin-bottom: 8px; border-bottom: 1px solid #eee; padding-bottom: 5px;">📍 선택한 위치</div>
                                <div style="font-size: 13px; color: #555; line-height: 1.6;">
                        `;

                        results.forEach(res => {
                            if (!res.text) return;
                            const type = res.type; // "road" or "parcel"
                            const label = (type === 'road') ? '도로명' : '지번';
                            const bgColor = (type === 'road') ? '#3498db' : '#95a5a6';

                            popupHtml += `
                                <div style="margin-bottom: 4px; display: flex; align-items: flex-start;">
                                    <span style="background: ${bgColor}; color: white; font-size: 10px; padding: 2px 4px; border-radius: 3px; font-weight: bold; margin-right: 6px; white-space: nowrap; margin-top: 2px;">${label}</span>
                                    <span>${res.text}</span>
                                </div>
                            `;
                        });

                        popupHtml += `</div></div>`;
                        content.innerHTML = popupHtml;
                    }
                } else {
                    overlay.setPosition(evt.coordinate);
                    content.innerHTML = '<div style="padding: 10px; font-size: 13px;">주소 정보를 찾을 수 없습니다.</div>';
                }
            }
        } catch (e) {
            console.error("주소 조회 오류:", e);
        }
    });
}

// 이벤트 바인딩
document.addEventListener('DOMContentLoaded', () => {
    const searchBtn = document.getElementById('search-button');
    const searchInp = document.getElementById('search-input');

    if (searchBtn) searchBtn.onclick = performSearch;
    if (searchInp) {
        searchInp.onkeypress = (e) => {
            if (e.key === 'Enter') performSearch();
        };
    }
});
