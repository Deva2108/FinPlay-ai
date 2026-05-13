package com.example.stockPortfolio.ContentManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentItemDTO {
    /** "video" | "podcast" */
    private String type;
    private String title;
    private String url;
    private String thumbnailUrl;
    private String source;       // YouTube channel / podcast publisher
    private String embedUrl;     // safe-to-iframe URL
    private Long publishedAt;    // epoch seconds, optional
}
