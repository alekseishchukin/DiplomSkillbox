package searchengine.dto.indexing;

import lombok.Data;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.PageService;

import java.util.List;

@Data
public class IndexingData {
    private IndexingResponse indexingResponse;
    private String url;
    private String siteName;
    private Site site;
    private PageService pageService;
    private List<Page> pageList;
}
