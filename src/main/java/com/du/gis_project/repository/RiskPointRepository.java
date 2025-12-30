package com.du.gis_project.repository;

import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.domain.entity.RiskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskPointRepository extends JpaRepository<RiskPoint, Long> {

    List<RiskPoint> findByType(RiskType type);
}
