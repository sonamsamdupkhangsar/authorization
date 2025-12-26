package me.sonam.auth.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @param page  page number
 * @param size  page size
 * @param total total elements of all
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomRestPage<T>(List<T> content, int page, int size, long total) {
    private static final Logger LOG = LoggerFactory.getLogger(CustomRestPage.class);

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CustomRestPage(@JsonProperty("content") List<T> content, int page, int size, long total) {
        this.page = page;
        this.size = size;
        this.content = content;

        this.total = total;
    }

}
