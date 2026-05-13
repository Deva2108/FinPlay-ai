package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Unlimited free news aggregator using Google News RSS.
 * Requires NO API key and handles high volumes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleNewsRssService {

    private final RestTemplate restTemplate;

    private static final String GOOGLE_NEWS_RSS_URL = "https://news.google.com/rss/search";

    public List<Map<String, Object>> fetchRssNews(String query) {
        String searchQuery = (query == null || query.isBlank()) ? "stock market" : query;
        
        String url = UriComponentsBuilder.fromHttpUrl(GOOGLE_NEWS_RSS_URL)
                .queryParam("q", searchQuery)
                .queryParam("hl", "en-IN")
                .queryParam("gl", "IN")
                .queryParam("ceid", "IN:en")
                .toUriString();

        try {
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) return Collections.emptyList();

            return parseGoogleNewsRss(xml);
        } catch (Exception e) {
            log.warn("Google News RSS failed for {}: {}", searchQuery, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseGoogleNewsRss(String xml) {
        List<Map<String, Object>> newsList = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < Math.min(items.getLength(), 10); i++) {
                Element item = (Element) items.item(i);
                
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("headline", getTagValue("title", item));
                simplified.put("url", getTagValue("link", item));
                simplified.put("datetime", getTagValue("pubDate", item));
                simplified.put("source", getTagValue("source", item));
                
                String title = String.valueOf(simplified.getOrDefault("headline", "")).toLowerCase();
                simplified.put("isRisk", title.contains("fall") || title.contains("drop") || title.contains("crash") || title.contains("loss") || title.contains("decline"));
                simplified.put("isOpportunity", title.contains("gain") || title.contains("surge") || title.contains("rise") || title.contains("profit") || title.contains("rally"));
                
                newsList.add(simplified);
            }
        } catch (Exception e) {
            log.error("Failed to parse Google News RSS: {}", e.getMessage());
        }
        return newsList;
    }

    private String getTagValue(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return "";
    }
}
