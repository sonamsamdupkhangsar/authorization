package me.sonam.auth.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true, value = {"pageable"})
public record CustomRestPage<T>(List<T> content, int number, int size, long totalElements, int numberOfElements, int totalPages)  {
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CustomRestPage(@JsonProperty("content") List<T> content,
                    @JsonProperty("number") int number,
                    @JsonProperty("size") int size,
                    @JsonProperty("totalElements") long totalElements

    ) {
        this(content, number, size, totalElements, content != null ? content.size() : 0, calculateTotalPages(totalElements, size));
    }
    public boolean isEmpty() {
        return content.isEmpty();
    }
    public static int calculateTotalPages(long totalElements, int pageSize) {
        if (pageSize == 0) {
            return 0; // Avoid division by zero
        }
        // Use integer arithmetic to ensure correct ceiling calculation
        return (int) Math.ceil((double) totalElements / pageSize);
    }

}