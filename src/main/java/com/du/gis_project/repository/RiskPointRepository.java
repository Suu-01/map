package com.du.gis_project.repository;

import com.du.gis_project.domain.entity.RiskPoint;
import com.du.gis_project.domain.entity.RiskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskPointRepository extends JpaRepository<RiskPoint, Long> {
    List<RiskPoint> findByType(RiskType type);

    void deleteByType(RiskType type);
}
