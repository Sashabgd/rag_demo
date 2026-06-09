package com.demo.rag.repository;

import com.demo.rag.entity.CustomModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomModelRepository extends JpaRepository<CustomModel, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
