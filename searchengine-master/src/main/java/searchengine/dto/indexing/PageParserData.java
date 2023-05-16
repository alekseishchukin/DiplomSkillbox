package searchengine.dto.indexing;

import lombok.Data;
import org.jsoup.nodes.Document;
import searchengine.model.Page;

@Data
public class PageParserData {
    private Page page;
    private Document document;
    private String error;
}
