package com.ehtp.kanban_backend.repository;

import com.ehtp.kanban_backend.model.Projet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjetRepository extends JpaRepository<Projet, Long> {
    @EntityGraph(attributePaths = {"owner", "members"})
    Optional<Projet> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Projet p where p.id = :id")
    Optional<Projet> findByIdForUpdate(@Param("id") Long id);
    @EntityGraph(attributePaths = {"owner", "members"})
    List<Projet> findDistinctByOwnerIdOrMembersId(Long ownerId, Long memberId);
    List<Projet> findByOwnerId(Long ownerId);
    List<Projet> findByMembersId(Long memberId);
}
