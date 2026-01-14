package com.du.gis_project.service;

import com.du.gis_project.domain.dto.RiskPointDto;
import com.du.gis_project.domain.entity.RiskType;
import com.du.gis_project.repository.RiskPointRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskService {

    private final RiskPointRepository riskPointRepository;

    public RiskService(RiskPointRepository riskPointRepository) {
        this.riskPointRepository = riskPointRepository;
    }

    public List<RiskPointDto> getAllRisks() {
        return riskPointRepository.findAll().stream()
                .map(RiskPointDto::new)
                .toList();
    }

    public List<RiskPointDto> getRisksByType(RiskType type) {
        return riskPointRepository.findByType(type).stream()
                .map(RiskPointDto::new)
                .toList();
    }
}
