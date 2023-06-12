package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class SuccessSearchingResponse extends SearchResponse {
    private boolean result;
    private int count;
    private List<SearchData> data;
}
