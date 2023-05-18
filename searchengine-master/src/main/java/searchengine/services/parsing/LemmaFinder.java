package searchengine.services.parsing;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingData;
import searchengine.dto.indexing.PageParserData;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LemmaFinder {
    private static final Logger LOGGER = LogManager.getLogger(LemmaFinder.class);
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    public static boolean running = true;

    public HashMap<String, Integer> getLemmasMap(String text) {
        HashMap<String, Integer> lemmasMap = new HashMap<>();
        LuceneMorphology luceneMorph = null;
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            LOGGER.error("Ошибка подключения к RussianLuceneMorphology: " + e.getMessage());
        }

        String clearedText = clearHtmlFromTags(text);

        String[] words = clearedText.replaceAll("[^А-яЁё]+", " ")
                .toLowerCase(Locale.ROOT).split("\\s+");

        for (String word : words) {
            if (!running) {
                break;
            }
            word = word.replaceAll("ё", "е");
            if (luceneMorph != null && isNecessaryWord(word, luceneMorph)) {
                List<String> lemmaList = luceneMorph.getNormalForms(word);
                for (String lemma : lemmaList) {
                    lemmasMap.put(lemma, lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1);
                }
            }
        }
        return lemmasMap;
    }

    public boolean isNecessaryWord(String word, LuceneMorphology luceneMorph) {
        if (word.length() > 1) {
            List<String> wordMorphInfoList = luceneMorph.getMorphInfo(word);
            for (String wordMorphInfo : wordMorphInfoList) {
                if (!wordMorphInfo.matches(".+[А-Я]$")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void lemmaAndIndexSave(HashMap<String, Integer> lemmaMap, Site site, Page page) {
        for (String lemmaString : lemmaMap.keySet()) {
            if (!running) break;
            List<Lemma> lemmaList = lemmaRepository.getLemmasByLemmaAndSiteID(lemmaString, site.getId());
            Lemma lemma;
            if (lemmaList.isEmpty()) {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(lemmaString);
                lemma.setFrequency(1);
            } else if (lemmaList.size() == 1) {
                lemma = lemmaList.get(0);
                lemma.setFrequency(lemma.getFrequency() + 1);
            } else {
                LOGGER.error("Дублированные леммы: ".concat(lemmaString));
                lemma = lemmaList.get(0);
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.saveAndFlush(lemma);
            indexSave(page, lemma, lemmaMap.get(lemmaString));
        }
    }

    public void indexSave(Page page, Lemma lemma, float rank) {
        Index index = new Index();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(rank);
        indexRepository.saveAndFlush(index);
    }

    public IndexingData parseLemmas(IndexingData indexingData) {
        if (!indexingData.getPageList().isEmpty()) {
            Page page = indexingData.getPageList().get(0);
            List<Integer> lemmaIds = indexRepository.getLemmaIdListByPageId(page.getId());
            for (int lemmaId : lemmaIds) {
                Optional<Lemma> lemmaOptional = lemmaRepository.findById(lemmaId);
                if (lemmaOptional.isPresent()) {
                    Lemma lemma = lemmaOptional.get();
                    int frequency = lemma.getFrequency() - 1;
                    if (frequency == 0) {
                        lemmaRepository.delete(lemma);
                    } else {
                        lemma.setFrequency(frequency);
                        lemmaRepository.saveAndFlush(lemma);
                    }
                }
            }
            pageRepository.delete(page);
        }
        PageParserData pageParserData = indexingData.getPageParser().getJsoupDocumentAndSavePage();
        HashMap<String, Integer> lemmasMap = getLemmasMap(pageParserData.getDocument().toString());
        lemmaAndIndexSave(lemmasMap, indexingData.getSite(), pageParserData.getPage());
        return indexingData;
    }

    public String clearHtmlFromTags(String text) {
        return Jsoup.parse(text).text();
    }
}
