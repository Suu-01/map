// ============================
// 관리자 기능 (데이터 입력 및 설정 토글)
// ============================

// 1. 데이터 가져오기 버튼 로직
const btnImport = document.getElementById('btn-import-data');
if (btnImport) {
    btnImport.addEventListener('click', function () {
        if (!confirm('데이터 가져오기를 시작하시겠습니까?\n(기존 데이터는 삭제되며, 시간이 수 분 걸릴 수 있습니다.)')) return;

        this.disabled = true;
        this.innerText = "가져오기 진행 중... (서버 로그 확인)";

        fetch('/api/import', { method: 'POST' })
            .then(res => res.json())
            .then(data => {
                // 데이터 최신화를 위해 기존 소스 초기화
                // 각 소스는 layers.js와 heatmap.js에 전역변수로 선언되어 있음
                if (typeof cctvSource !== 'undefined') cctvSource.clear();
                if (typeof policeSource !== 'undefined') policeSource.clear();
                if (typeof lightSource !== 'undefined') lightSource.clear();
                if (typeof refinedRiskSource !== 'undefined') refinedRiskSource.clear();

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

// 2. 어드민 섹션 토글 로직 (숨김/표시 제어)
const toggleAdmin = document.getElementById('toggle-admin');
const adminSection = document.getElementById('admin-section');
if (toggleAdmin && adminSection) {
    toggleAdmin.addEventListener('click', function () {
        const isHidden = adminSection.style.display === 'none';
        adminSection.style.display = isHidden ? 'block' : 'none';

        // 클릭된 텍스트에 하이라이트 효과 부여
        this.classList.toggle('active', !isHidden);
    });
}
