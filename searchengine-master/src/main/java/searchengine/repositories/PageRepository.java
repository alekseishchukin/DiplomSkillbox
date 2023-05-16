package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query(value = "SELECT * FROM page WHERE path = :path", nativeQuery = true)
    List<Page> getPagesByPath(String path);

    @Query(value = "SELECT * FROM page WHERE path = :path AND site_id = :siteId", nativeQuery = true)
    List<Page> getPagesByPathAndSiteId(String path, int siteId);

    @Query(value = "SELECT COUNT(*) FROM page WHERE site_id = :siteId", nativeQuery = true)
    int countPagesBySiteId(int siteId);
}
