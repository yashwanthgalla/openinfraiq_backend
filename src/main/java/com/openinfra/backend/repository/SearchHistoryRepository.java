package com.openinfra.backend.repository;

import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.SearchHistory;
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
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findByUserOrderByLastViewedAtDesc(User user);

    Page<SearchHistory> findByUserOrderByLastViewedAtDesc(User user, Pageable pageable);

    List<SearchHistory> findByUser_FirebaseUidOrderByLastViewedAtDesc(String firebaseUid);

    Optional<SearchHistory> findByUserAndRepository(User user, Repository repository);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.user = :user AND sh.id = :id")
    int deleteByUserAndId(@Param("user") User user, @Param("id") Long id);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.user = :user")
    int deleteByUser(@Param("user") User user);
}
