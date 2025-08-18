package SingSongGame.BE.song.application;

import SingSongGame.BE.in_game.application.SongCacheManager;
import SingSongGame.BE.song.application.dto.request.SongVerifyRequest;
import SingSongGame.BE.song.application.dto.response.SongResponse;
import SingSongGame.BE.song.application.dto.response.SongVerifyResponse;
import SingSongGame.BE.song.persistence.Song;
import SingSongGame.BE.song.persistence.SongRepository;
import SingSongGame.BE.song.persistence.Tag;
import SingSongGame.BE.song.persistence.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongService {

    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final SongCacheManager songCacheManager;

    @Transactional
    public Song getRandomSong() {
        return getRandomSong(Collections.emptySet(), null);
    }

    @Transactional(readOnly = true)
    public Song getRandomSong(Set<Long> usedSongIds, String excludeArtist) {
        // 1. 먼저 캐시에서 시도
        Song cachedSong = songCacheManager.getRandomSongFromCache(usedSongIds, excludeArtist, null);

        if (cachedSong != null) {
            return cachedSong;
        }

        // 2. 캐시에서 못 찾으면 DB 조회 (fallback)
        log.warn("⚠️ 캐시에서 노래를 찾지 못했습니다. DB 조회를 시도합니다.");
        List<Song> candidates = songRepository.findRandomCandidates(usedSongIds, excludeArtist);

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(new Random().nextInt(candidates.size()));
    }

    @Transactional(readOnly = true)
    public Song getRandomSongByTagNames(Set<String> keywordNames, Set<Long> usedSongIds, String excludeArtist) {
        // 1. 캐시 우선 시도
        Song cachedSong = songCacheManager.getRandomSongFromCache(usedSongIds, excludeArtist, keywordNames);

        if (cachedSong != null) {
            return cachedSong;
        }

        // 2. DB fallback
        log.warn("⚠️ 캐시에서 키워드 노래를 찾지 못했습니다. DB 조회를 시도합니다.");

        if (keywordNames == null || keywordNames.isEmpty() || keywordNames.contains("전체")) {
            List<Song> allSongs = songRepository.findAllWithTagsExcluding(usedSongIds);
            return selectSongExcludingArtist(allSongs, excludeArtist);
        }

        List<Tag> tags = tagRepository.findByNameIn(keywordNames);
        List<Long> tagIds = tags.stream().map(Tag::getId).toList();

        List<Song> candidates = songRepository.findSongsByTagIds(tagIds)
                .stream()
                .filter(song -> !usedSongIds.contains(song.getId()))
                .toList();

        return selectSongExcludingArtist(candidates, excludeArtist);
    }

    private Song selectSongExcludingArtist(List<Song> candidates, String excludeArtist) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("출제 가능한 노래가 없습니다.");
        }

        // 이전 가수와 다른 노래만 필터링
        List<Song> filteredCandidates = candidates;

        if (excludeArtist != null && !excludeArtist.isBlank()) {
            filteredCandidates = candidates.stream()
                    .filter(song -> !excludeArtist.equals(song.getArtist()))
                    .toList();
        }

        // 필터링된 후보가 없으면 전체 후보 사용
        if (filteredCandidates.isEmpty()) {
            System.out.println("⚠️ 이전 가수 제외 후 후보가 없음. 전체 후보 사용");
            filteredCandidates = candidates;
        }

        return filteredCandidates.get(new Random().nextInt(filteredCandidates.size()));
    }

    @Transactional(readOnly = true)
    public SongResponse createSongResponse(Song song, Integer round, Integer maxRound) {
        // ✅ 이미 선택된 song 객체로 DTO 변환만
        return SongResponse.from(song, round, maxRound);
    }

    public SongVerifyResponse verifyAnswer(SongVerifyRequest request) {
        Song song = songRepository.findById(request.songId())
                .orElseThrow(() -> new IllegalArgumentException("해당 곡이 존재하지 않습니다."));

        String correctAnswer = normalize(song.getAnswer());
        String userInput = normalize(request.userAnswer());

        boolean isCorrect = correctAnswer.equals(userInput);

        return new SongVerifyResponse(isCorrect, song.getTitle());
    }

    private String normalize(String input) {
        return input
                .toLowerCase()          // 대소문자 무시
                .replaceAll("[^a-z0-9가-힣]", "");
    }
}
