package au.org.garvan.kccg.annotations.pipeline.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Created by ahmed on 8/1/18.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor
@ApiModel
@JsonIgnoreProperties(ignoreUnknown = true)
@Deprecated
public class PaginatedSearchResult {

    List<SearchResult> articles;
    PaginationRequestParams pagination;
    List<ConceptFilter> filters;

}