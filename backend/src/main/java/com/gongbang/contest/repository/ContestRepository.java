package com.gongbang.contest.repository;

import com.gongbang.contest.domain.Contest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findBySourceUrl(String sourceUrl);
}
