package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Query(value = "SELECT * FROM lemma WHERE `lemma` = :lemma AND site_id = :siteId", nativeQuery = true)
    List<Lemma> getLemmasByLemmaAndSiteID(String lemma, int siteId);

    @Query(value = "SELECT id FROM lemma " +
            "WHERE `lemma` IN :lemmaString", nativeQuery = true)
    List<Integer> getLemmaIdsByLemmaStringList(List<String> lemmaString);

    @Query(value = "SELECT id FROM lemma " +
            "WHERE `lemma` = :lemmaString " +
            "AND `site_id` IN :siteIds", nativeQuery = true)
    List<Integer> getLemmaIdsByLemmaStringAndSiteIds(String lemmaString, List<Integer> siteIds);

    @Query(value = "SELECT * FROM lemma " +
            "WHERE id IN :lemmaIds " +
            "AND `site_id` IN :siteIds " +
            "AND `frequency` < :restriction " +
            "ORDER BY `frequency`", nativeQuery = true)
    List<Lemma> getLemmasByLemmaAndSiteIDWithRestriction(List<Integer> lemmaIds, List<Integer> siteIds, int restriction);

    @Query(value = "SELECT COUNT(*) FROM lemma WHERE site_id = :siteId", nativeQuery = true)
    int countLemmasBySiteId(int siteId);
}
