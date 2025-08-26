package SingSongGame.BE.in_game.application;

import SingSongGame.BE.song.persistence.Song;
import SingSongGame.BE.song.persistence.SongRepository;
import SingSongGame.BE.song.persistence.Tag;
import SingSongGame.BE.song.persistence.TagRepository;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class SongCacheManager {

    private final SongRepository songRepository;
    private final TagRepository tagRepository;

    // 메모리 캐시
    private List<Song> allSongs;
    private Map<Long, Song> songById;
    private Map<Long, Set<Song>> songsByTagId;
    private volatile boolean cacheInitialized = false;

    @PostConstruct
    public void initializeCache() {
        log.info("🎵 노래 캐시 초기화 시작");

        // 한 번의 쿼리로 모든 노래와 태그 로드
        this.allSongs = songRepository.findAllWithTags();

        // ID별 인덱싱
        this.songById = allSongs.stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));

        // 태그별 인덱싱
        this.songsByTagId = new ConcurrentHashMap<>();
        for (Song song : allSongs) {
            for (Tag tag : song.getTags()) {
                songsByTagId.computeIfAbsent(tag.getId(), k -> ConcurrentHashMap.newKeySet())
                        .add(song);
            }
        }

        this.cacheInitialized = true;
        log.info("✅ 노래 캐시 초기화 완료: {}곡 로드", allSongs.size());
    }

    // 캐시에서 랜덤 노래 선택
    public Song getRandomSongFromCache(Set<Long> usedSongIds, String excludeArtist, Set<String> keywords) {
        if (!cacheInitialized) {
            log.warn("⚠️ 캐시가 초기화되지 않았습니다. DB에서 직접 조회합니다.");
            return null; // fallback to DB query
        }

        Stream<Song> stream = allSongs.stream()
                .filter(song -> !usedSongIds.contains(song.getId()));

        // 아티스트 제외
        if (excludeArtist != null && !excludeArtist.isBlank()) {
            stream = stream.filter(song -> !excludeArtist.equals(song.getArtist()));
        }

        // 키워드 필터링
        if (keywords != null && !keywords.isEmpty() && !keywords.contains("전체")) {
            Set<Long> tagIds = getTagIdsByNames(keywords);
            Set<Song> keywordSongs = tagIds.stream()
                    .flatMap(tagId -> songsByTagId.getOrDefault(tagId, Set.of()).stream())
                    .collect(Collectors.toSet());
            stream = stream.filter(keywordSongs::contains);
        }

        List<Song> candidates = stream.collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private Set<Long> getTagIdsByNames(Set<String> keywords) {
        // 태그 이름으로 ID 찾기 (이 부분만 DB 조회 필요)
        return new HashSet<>(tagRepository.findIdsByNameIn(keywords));
    }
}
