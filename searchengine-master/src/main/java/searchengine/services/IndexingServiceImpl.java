package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingData;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private static final Logger LOGGER = LogManager.getLogger(IndexingServiceImpl.class);
    private static final Marker INVALID_DATA_MARKER = MarkerManager.getMarker("INVALID_DATA_MARKER");
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sites;
    private static int countInstances = 0;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse indexingResponse = checkingIndexingRunning();
        if (!indexingResponse.isResult()) return indexingResponse;

        for (searchengine.config.Site site : sites.getSites()) {
            countInstances++;
            IndexingData indexingData = new IndexingData();
            indexingData.setIndexingResponse(indexingResponse);
            indexingData.setUrl(site.getUrl());
            indexingData.setSiteName(site.getName());

            SiteService siteService = new SiteService(siteRepository, pageRepository,
                    lemmaRepository, indexRepository);

            indexingData = checkingAvailabilitySiteInDB(indexingData);
            if (!indexingData.getIndexingResponse().isResult()) {
                PageService.running = false;
                LemmaService.running = false;
                return indexingData.getIndexingResponse();
            }
            if (indexingData.getSite() != null) {
                siteRepository.delete(indexingData.getSite());
            }
            siteService.createSiteDB(site.getName(), site.getUrl());
            siteService.start();
        }
        return indexingResponse;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (countInstances > 0) {
            indexingResponse.setResult(true);
            indexingResponse.setError(null);
        } else {
            indexingResponse.setResult(true);
            indexingResponse.setError("Индексация не запущена");
        }
        PageService.running = false;
        LemmaService.running = false;
        return indexingResponse;
    }

    @Override
    public IndexingResponse indexPage(String path) {
        IndexingResponse indexingResponse = checkingIndexingRunning();
        if (!indexingResponse.isResult()) return indexingResponse;

        IndexingData indexingData = checkingCorrectnessPath(path);
        if (!indexingData.getIndexingResponse().isResult()) return indexingData.getIndexingResponse();

        SiteService siteService = new SiteService(siteRepository, pageRepository,
                lemmaRepository, indexRepository);

        indexingData = checkingAvailabilitySiteInDB(indexingData);
        if (!indexingData.getIndexingResponse().isResult()) return indexingData.getIndexingResponse();
        countInstances++;

        Site site = indexingData.getSite() != null ? indexingData.getSite() :
                siteService.createSiteDB(indexingData.getSiteName(), indexingData.getUrl());

        indexingData.setPageList(pageRepository.getPagesByPathAndSiteId(
                path.replaceFirst(site.getUrl(), "/"), site.getId()));
        indexingData.setPageService(new PageService(siteRepository, pageRepository,
                lemmaRepository, indexRepository, path, site));
        LemmaService lemmaService = new LemmaService(lemmaRepository, indexRepository, pageRepository);
        indexingData = lemmaService.parseLemmas(indexingData);

        countInstances--;
        if (!indexingData.getIndexingResponse().isResult()) return indexingData.getIndexingResponse();

        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        site.setStatus(IndexingStatus.INDEXED);
        siteRepository.saveAndFlush(site);
        return indexingResponse;
    }

    public IndexingResponse checkingIndexingRunning() {
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(true);
        if (countInstances > 0) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
            return indexingResponse;
        }
        indexingResponse.setResult(true);
        PageService.linkSet = new CopyOnWriteArraySet<>();
        PageService.running = true;
        LemmaService.running = true;
        return indexingResponse;
    }

    public IndexingData checkingCorrectnessPath(String path) {
        IndexingData indexingData = new IndexingData();
        indexingData.setIndexingResponse(new IndexingResponse(true, null));

        Matcher matcher = Pattern.compile("(.+//[^/]+/).*").matcher(path);
        if (matcher.find()) {
            indexingData.setUrl(matcher.group(1));
        } else {
            indexingData.getIndexingResponse().setResult(false);
            indexingData.getIndexingResponse().setError("Адрес страницы указан неверно. " +
                    "Пример: https://example.com/news/");
            LOGGER.info(INVALID_DATA_MARKER, "Недопустимый адрес: " + path);
            return indexingData;
        }

        boolean isSiteExist = false;
        for (searchengine.config.Site site : sites.getSites()) {
            if (site.getUrl().equals(indexingData.getUrl())) {
                isSiteExist = true;
                indexingData.setSiteName(site.getName());
            }
        }

        if (!isSiteExist) {
            indexingData.getIndexingResponse().setResult(false);
            indexingData.getIndexingResponse().setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            LOGGER.info(INVALID_DATA_MARKER, "Адрес отсутствует в конфигурации: " + path);
        }
        return indexingData;
    }

    public IndexingData checkingAvailabilitySiteInDB(IndexingData indexingData) {
        List<Site> siteList = siteRepository.getSiteListByUrl(indexingData.getUrl());
        Site site;
        if (siteList.size() > 1) {
            indexingData.getIndexingResponse().setResult(false);
            indexingData.getIndexingResponse().setError("В базе данных содержатся сайты с одинаковыми URL");
            LOGGER.error("Дублирование адресов в БД: " + indexingData.getUrl());
            return indexingData;
        } else if (siteList.size() == 1) {
            site = siteList.get(0);
            site.setStatus(IndexingStatus.INDEXING);
            site.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteRepository.saveAndFlush(site);
            indexingData.setSite(site);
        }
        return indexingData;
    }

    public static void decrementCountInstances() {
        countInstances--;
    }
}
