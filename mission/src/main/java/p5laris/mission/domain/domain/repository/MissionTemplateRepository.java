package p5laris.mission.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.mission.domain.domain.entity.MissionTemplate;

import java.util.List;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {

    List<MissionTemplate> findByActiveTrueOrderByIdAsc();
}
