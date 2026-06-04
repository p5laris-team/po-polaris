package p5laris.ai.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.PromptCategory;

import java.util.Optional;

/**
 * prompt_templates 조회 repository다.
 */
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    // 카테고리별 최신 활성 프롬프트를 가져온다.
    Optional<PromptTemplate> findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(PromptCategory category);
}
