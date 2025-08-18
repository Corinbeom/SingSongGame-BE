package SingSongGame.BE.song.persistence;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
    List<Tag> findByNameIn(Set<String> names);

    // ✅ 새로 추가할 메서드: 태그 이름으로 ID만 조회 (성능 최적화)
    @Query("SELECT t.id FROM Tag t WHERE t.name IN :names")
    List<Long> findIdsByNameIn(@Param("names") Set<String> names);
}
