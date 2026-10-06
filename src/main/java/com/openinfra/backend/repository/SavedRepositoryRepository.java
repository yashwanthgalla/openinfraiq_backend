package com.openinfra.backend.repository;

import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.SavedRepository;
import com.openinfra.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface SavedRepositoryRepository extends JpaRepository<SavedRepository, Long> {

    List<SavedRepository> findByUserOrderBySavedAtDesc(User user);

    Page<SavedRepository> findByUserOrderBySavedAtDesc(User user, Pageable pageable);

    List<SavedRepository> findByUser_FirebaseUidOrderBySavedAtDesc(String firebaseUid);

    Optional<SavedRepository> findByUserAndRepository(User user, Repository repository);

    boolean existsByUserAndRepository(User user, Repository repository);

    @Query("SELECT CASE WHEN COUNT(sr) > 0 THEN true ELSE false END FROM SavedRepository sr " +
           "WHERE sr.user.firebaseUid = :firebaseUid AND LOWER(sr.repository.owner) = LOWER(:owner) AND LOWER(sr.repository.name) = LOWER(:name)")
    boolean existsByFirebaseUidAndOwnerAndName(@Param("firebaseUid") String firebaseUid, @Param("owner") String owner, @Param("name") String name);

    @Modifying
    @Query("DELETE FROM SavedRepository sr WHERE sr.user = :user AND sr.id = :id")
    int deleteByUserAndId(@Param("user") User user, @Param("id") Long id);

    @Modifying
    @Query("DELETE FROM SavedRepository sr WHERE sr.user = :user AND sr.repository = :repository")
    int deleteByUserAndRepository(@Param("user") User user, @Param("repository") Repository repository);
}
