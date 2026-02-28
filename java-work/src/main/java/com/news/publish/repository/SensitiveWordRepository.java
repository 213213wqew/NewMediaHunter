package com.news.publish.repository;

import com.news.publish.model.entity.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {
    Optional<SensitiveWord> findByWord(String word);
}
