package com.du.gis_project.repository;

import com.du.gis_project.domain.entity.PopulationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PopulationPointRepository extends JpaRepository<PopulationPoint, Long> {
}
