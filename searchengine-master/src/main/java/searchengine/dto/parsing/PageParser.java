package searchengine.dto.parsing;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.dto.indexing.PageParserData;
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

public class PageParser extends RecursiveTask<Site> {
    private static final Logger LOGGER = LogManager.getLogger(PageParser.class);
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    public static Set<String> linkSet = new CopyOnWriteArraySet<>();
    private final String url;
    private final Site site;
    private Page page;
    public static boolean running = true;

    public PageParser(SiteRepository siteRepository, PageRepository pageRepository,
                      LemmaRepository lemmaRepository, IndexRepository indexRepository,
                      String url, Site site) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.url = url;
        this.site = site;
    }

    @Override
    protected Site compute() {
        linkSet.add(url);
        List<PageParser> pageParserList = new CopyOnWriteArrayList<>();
        if (!running) stopIndexing();
        String path = url.replaceFirst(site.getUrl(), "/");
        AtomicBoolean pageTestBoolean = new AtomicBoolean(false);
        for (Page pageTest : pageRepository.getPagesByPath(path)) {
            if (pageTest.getSite().getId() == site.getId()) {
                pageTestBoolean.set(true);
            }
        }
        if (running && !pageTestBoolean.get()) {
            Document document = getJsoupDocumentAndSavePage().getDocument();
            if (document != null) {
                for (Element element : document.select("a[href]")) {
                    if (!running) {
                        break;
                    }
                    String link = element.absUrl("href");
                    AtomicInteger pointCount = new AtomicInteger(
                            StringUtils.countMatches(link.replace(url, ""), "."));
                    if (!link.isEmpty() && link.startsWith(url) && !link.contains("#") && pointCount.get() == 0
                            && running && !link.equals(url) && !linkSet.contains(link)) {
                        linkSet.add(link);
                        PageParser pageParser = new PageParser(siteRepository, pageRepository,
                                lemmaRepository, indexRepository, link, site);
                        pageParser.fork();
                        pageParserList.add(pageParser);
                    }
                }
            }
            for (PageParser link : pageParserList) {
                if (!running) break;
                link.join();
            }
        }
        return site;
    }

    public PageParserData getJsoupDocumentAndSavePage() {
        PageParserData pageParserData = new PageParserData();
        if (url.replaceFirst(site.getUrl(), "").startsWith("?")) return pageParserData;
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            LOGGER.error("Поток прерван: " + Thread.currentThread().getName() + " - " + url);
            pageParserData.setError(e.getMessage());
        }
        Connection connection = Jsoup.connect(url).ignoreHttpErrors(true).ignoreContentType(true);
        Document document = null;
        try {
            document = connection.get();
        } catch (IOException e) {
            LOGGER.error("Ошибка подключения: " + url + " - " + e.getMessage());
            pageParserData.setError(e.getMessage());
        }
        if (connection.response().statusCode() == 200) {
                page = new Page();
                page.setSite(site);
                page.setPath(url.replaceFirst(site.getUrl(), "/"));
                page.setCode(connection.response().statusCode());
                page.setContent(document != null ? document.toString() : null);
                site.setStatusTime(new Timestamp(new Date().getTime()).toString());
                pageParserData.setPage(page);
                pageParserData.setDocument(document);
                pageRepository.saveAndFlush(page);
                siteRepository.saveAndFlush(site);
                LemmaFinder lemmaFinder = new LemmaFinder(lemmaRepository, indexRepository, pageRepository);
                HashMap<String, Integer> lemmaMap = lemmaFinder.getLemmasMap(
                        document != null ? document.toString() : null);
                lemmaFinder.lemmaAndIndexSave(lemmaMap, site, page);
            }
        return pageParserData;
    }

    public void stopIndexing() {
        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        site.setLastError("Индексация остановлена пользователем");
        site.setStatus(IndexingStatus.FAILED);
        siteRepository.saveAndFlush(site);
    }

    public Page getPage() {
        return page;
    }
}