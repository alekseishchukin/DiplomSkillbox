package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import searchengine.model.IndexingStatus;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class SiteService extends Thread {
    private static final Logger LOGGER = LogManager.getLogger(SiteService.class);
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private Site site;

    @Override
    public void run() {
        PageService pageService = new PageService(siteRepository, pageRepository,
                lemmaRepository, indexRepository, site.getUrl(), site);

        try {
            new ForkJoinPool(Runtime.getRuntime().availableProcessors()).submit(pageService).get();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Ошибка индексации сайта: ".concat(site.getUrl())
                    .concat(System.lineSeparator()).concat(e.getMessage()));
            site.setStatusTime(new Timestamp(new Date().getTime()).toString());
            site.setLastError("Ошибка индексации сайта");
            site.setStatus(IndexingStatus.FAILED);
        }

        if (site.getStatus().equals(IndexingStatus.INDEXING)) {
            site.setStatus(IndexingStatus.INDEXED);
        }
        siteRepository.saveAndFlush(site);
        IndexingServiceImpl.decrementCountInstances();
    }

    public Site createSiteDB(String siteName, String siteUrl) {
        site = new Site();
        site.setName(siteName);
        site.setUrl(siteUrl);
        site.setStatus(IndexingStatus.INDEXING);
        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteRepository.saveAndFlush(site);
        return site;
    }
}
