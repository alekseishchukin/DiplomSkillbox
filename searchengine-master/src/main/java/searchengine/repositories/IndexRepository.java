package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    @Query(value = "SELECT lemma_id FROM `index` WHERE page_id = :pageId", nativeQuery = true)
    List<Integer> getLemmaIdListByPageId(int pageId);

    @Query(value = "SELECT page_id FROM `index` WHERE lemma_id IN :lemmaIds", nativeQuery = true)
    List<Integer> getPageIdsByLemma(List<Integer> lemmaIds);

    @Query(value = "SELECT SUM(`rank`) FROM `index` WHERE page_id = :pageId", nativeQuery = true)
    int getAbsoluteRelevance(int pageId);

    @Query(value = "SELECT SUM(`rank`) FROM `index` WHERE page_id IN :pageIds", nativeQuery = true)
    int getRelativeRelevance(List<Integer> pageIds);
}
