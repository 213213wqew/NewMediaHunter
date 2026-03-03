package com.news.publish.repository;

import com.news.publish.model.entity.PlatformTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlatformTaskRepository extends JpaRepository<PlatformTask, Long> {
    List<PlatformTask> findByPlatformKey(String platformKey);
    void deleteByPlatformKey(String platformKey);
}
