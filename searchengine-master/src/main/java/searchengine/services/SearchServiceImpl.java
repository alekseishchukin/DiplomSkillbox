package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private static final Logger LOGGER = LogManager.getLogger(SearchServiceImpl.class);
    private static final Marker INPUT_HISTORY_MARKER = MarkerManager.getMarker("INPUT_HISTORY_MARKER");
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private LemmaService lemmaService;
    private final SitesList sites;

    public SearchResponse search(String query, String url, int offset, int limit) {
        PageService.running = true;
        LemmaService.running = true;
        LOGGER.info(INPUT_HISTORY_MARKER, "Query: ".concat(query.equals("") ? "Empty" : query)
                .concat("; URL: ").concat(url == null ? "All sites" : url)
                .concat("; Offset: ").concat(String.valueOf(offset))
                .concat("; Limit: ").concat(String.valueOf(limit)));
        lemmaService = new LemmaService(lemmaRepository, indexRepository, pageRepository);
        if (query.equals("")) {
            SearchResponse searchResponse = new SearchResponse();
            searchResponse.setResult(false);
            searchResponse.setError("Пустой поисковый запрос");
            return searchResponse;
        }
        List<Lemma> lemmas = getLemmaIdsByQuery(query, url, 100);
        Map<String, Integer> lemmaMap = new HashMap<>(sortingLemmas(lemmas));

        boolean runningMapStream = true;
        List<Integer> pageIdsResult = new ArrayList<>();
        for (String lemmaFirst : lemmaMap.keySet()) {
            if (!runningMapStream) break;

            List<Integer> lemmaIds = lemmaRepository.getLemmaIdsByLemmaStringAndSiteIds(lemmaFirst, getSiteIds(url));
            pageIdsResult = indexRepository.getPageIdsByLemma(lemmaIds);
            runningMapStream = false;
        }
        pageIdsResult = setResultPageIds(lemmaMap, pageIdsResult, url);
        return okSearchResponse(pageIdsResult, offset, limit, lemmaMap);
    }

    public SearchResponse okSearchResponse(List<Integer> pageIds, int offset, int limit, Map<String, Integer> lemmaMap) {
        SearchResponse searchResponse = new SearchResponse();
        List<SearchData> searchDataList = new ArrayList<>();
        int countResult = 0;
        for (Integer pageId : pageIds) {
            countResult++;
            if (countResult < offset) continue;
            if (countResult > limit + offset) break;
            SearchData searchData = new SearchData();
            Optional<Page> page = pageRepository.findById(pageId);
            if (page.isPresent()) {
                Site site = page.get().getSite();
                searchData.setSite(site.getUrl());
                searchData.setSiteName(site.getName());
                searchData.setUri(page.get().getPath().substring(1));
                searchData.setTitle(Jsoup.parse(page.get().getContent()).title());
                searchData.setSnippet(createSnippet(page.get().getContent(), lemmaMap));
                searchData.setRelevance((float) indexRepository.getAbsoluteRelevance(pageId)
                        / indexRepository.getRelativeRelevance(pageIds));
                searchDataList.add(searchData);
            }
        }
        searchDataList.sort((o1, o2) -> Float.compare(o1.getRelevance(), o2.getRelevance()));
        searchResponse.setResult(true);
        searchResponse.setCount(searchDataList.size());
        searchResponse.setData(searchDataList);
        return searchResponse;
    }

    public List<Lemma> getLemmaIdsByQuery(String query, String url, int indexRestriction) {
        HashMap<String, Integer> queryLemmaMap = lemmaService.getLemmasMap(query);
        List<String> lemmaListQuery = new ArrayList<>(queryLemmaMap.keySet());
        List<Integer> lemmaIds = lemmaRepository.getLemmaIdsByLemmaStringList(lemmaListQuery);
        return lemmaRepository.getLemmasByLemmaAndSiteIDWithRestriction(lemmaIds, getSiteIds(url), indexRestriction);
    }

    public Map<String, Integer> sortingLemmas(List<Lemma> lemmaList) {
        Map<String, Integer> lemmaMap = new HashMap<>();
        for (Lemma lemma : lemmaList) {
            int totalFrequency = lemmaMap.containsKey(lemma.getLemma()) ?
                    lemmaMap.get(lemma.getLemma()) + lemma.getFrequency()
                    : lemma.getFrequency();
            lemmaMap.put(lemma.getLemma(), totalFrequency);
        }
        return lemmaMap.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> {
                            throw new AssertionError();
                        }, LinkedHashMap::new));
    }

    public List<Integer> setResultPageIds(Map<String, Integer> lemmaMap, List<Integer> pageIdsByFirstLemma, String url) {
        boolean runningMapStream = false;
        List<Integer> pageIdsByPreviousLemma = new ArrayList<>(pageIdsByFirstLemma);
        List<Integer> resultPages = new ArrayList<>();
        for (String lemma : lemmaMap.keySet()) {
            if (!runningMapStream && !resultPages.isEmpty()) {
                runningMapStream = true;
                continue;
            }
            List<Integer> lemmaIds = lemmaRepository.getLemmaIdsByLemmaStringAndSiteIds(lemma, getSiteIds(url));
            List<Integer> pageIdsByLemma = indexRepository.getPageIdsByLemma(lemmaIds);
            for (int pageId : pageIdsByLemma) {
                if (pageIdsByPreviousLemma.contains(pageId)) {
                    resultPages.add(pageId);
                }
            }
            pageIdsByPreviousLemma = new ArrayList<>(resultPages);
        }
        return resultPages;
    }

    public List<Integer> getSiteIds(String url) {
        List<String> siteUrls = new ArrayList<>();
        if (url == null) {
            sites.getSites().forEach(site -> siteUrls.add(site.getUrl()));
        } else {
            siteUrls.add(url);
        }
        return siteRepository.getSitesByUrls(siteUrls);
    }

    public String createSnippet(String text, Map<String, Integer> lemmaMap) {
        text = lemmaService.clearHtmlFromTags(text);
        StringBuilder builder = new StringBuilder();
        for (String lemma : lemmaMap.keySet()) {
            String firstChar = String.valueOf(lemma.charAt(0)).toUpperCase(Locale.ROOT);
            String regex = " (.{1,30})(".concat(lemma).concat("|")
                    .concat(lemma.replaceFirst(String.valueOf(lemma.charAt(0)), firstChar)).concat(")(.{1,30} )");
            Matcher matcher = Pattern.compile(regex).matcher(text);
            if (matcher.find()) {
                String snippet = " ..".concat(matcher.group(1)).concat("<b>").concat(matcher.group(2))
                        .concat("</b>").concat(matcher.group(3)).concat(".. ");
                builder.append(snippet);
            } else {
                builder.append(" ..<b>").append(lemma).append("</b>..  ");
            }
        }
        return builder.toString();
    }
}
