package com.news.publish.repository;

import com.news.publish.model.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformRepository extends JpaRepository<Platform, Long> {
    Optional<Platform> findByPlatformKey(String platformKey);
}
