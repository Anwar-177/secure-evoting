package com.evoting.repository;

import com.evoting.model.Election;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElectionRepository extends JpaRepository<Election, Long> {
    List<Election> findAllByOrderByCreatedAtDesc();
    List<Election> findByStatusOrderByCreatedAtDesc(Election.Status status);
    long countByStatus(Election.Status status);
}
