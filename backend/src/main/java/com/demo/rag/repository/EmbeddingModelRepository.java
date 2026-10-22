package com.demo.rag.repository;

import com.demo.rag.entity.EmbeddingModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingModelRepository extends JpaRepository<EmbeddingModel, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<EmbeddingModel> findFirstByActiveTrue();
}
