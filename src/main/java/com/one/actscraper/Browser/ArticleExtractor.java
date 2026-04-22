package com.one.actscraper.Browser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ArticleExtractor {

    private static final double MAX_LINK_DENSITY = 0.5;
    private static final int MIN_PARAGRAPH_LENGTH = 40;

    public static String extract(String html) {
        Document doc = Jsoup.parse(html);

        Element root = doc.selectFirst("article, main, [role=main], #main, #content, .post-content, .article-body");
        if (root == null) root = doc.body();


        if (root == doc.body()) {
            Element best = null;
            int bestScore = 0;
            for (Element candidate : root.select("div, section, td")) {
                int nestedCandidates = 0;
                for (Element child : candidate.children()) {
                    if (child.normalName().equals("div") || child.normalName().equals("section")) {
                        nestedCandidates++;
                    }
                }

                int score = 0;
                for (Element p : candidate.select("p")) {
                    String t = p.text().trim();
                    if (t.length() > 25) {
                        score += t.length();
                        score += 10;
                    }
                }

                if (nestedCandidates > 2) {
                    score = (int) (score * 0.5);
                }

                String fullText = candidate.text();
                String linkText = candidate.select("a").text();
                if (!fullText.isEmpty()) {
                    double ld = (double) linkText.length() / fullText.length();
                    if (ld > 0.3) score = (int) (score * 0.2);
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best != null && bestScore > 200) root = best;
        }

        root.select(String.join(", ",
                "nav", "header", "footer", "aside",
                "script", "style", "noscript", "template",
                "iframe", "object", "embed", "canvas", "svg",
                "input", "button", "select", "textarea", "form",
                "figure > img", "figure > picture", "video", "audio",
                "[aria-hidden=true]", "[role=complementary]", "[role=navigation]",
                "[role=banner]", "[role=contentinfo]"
        )).remove();

        root.select(String.join(", ",
                "[class*=advertisement]", "[class*=sponsored]",
                "[class*=-ad-]", "[class*=_ad_]",
                "[id^=ad-]", "[class^=ad-]",
                "[class*=cookie]", "[class*=gdpr]", "[class*=consent]",
                "[class*=tracking]",
                "[class*=popup]", "[class*=modal]", "[class*=overlay]",
                "[class*=paywall]", "[class*=gate]",
                "[class*=sidebar]", "[class*=banner]",
                "[class*=breadcrumb]", "[class*=pagination]",
                "[class*=toc]", "[id*=toc]",
                "[class*=share]", "[class*=social]",
                "[class*=comment]", "[class*=disqus]",
                "[class*=related]", "[class*=recommended]",
                "[class*=newsletter]", "[class*=subscribe]",
                "[class*=author]", "[class*=byline]",
                "[class*=dateline]", "time"
        )).remove();

        for (Element el : root.select("div, section, ul, ol, p")) {
            String fullText = el.text();
            String linkText = el.select("a").text();
            if (!fullText.isEmpty()) {
                double linkDensity = (double) linkText.length() / fullText.length();
                if (linkDensity > MAX_LINK_DENSITY) {
                    el.remove();
                }
            }
        }

        for (Element p : root.select("p, li")) {
            if (p.text().trim().length() < MIN_PARAGRAPH_LENGTH) {
                p.remove();
            }
        }

        boolean removed = true;
        while (removed) {
            removed = false;
            for (Element el : root.select("div, section, p, span, li, ul, ol")) {
                if (el.text().isBlank()) {
                    el.remove();
                    removed = true;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Element el : root.select("p, h1, h2, h3, h4, h5, h6, li, blockquote, pre")) {
            String t = el.text().trim();
            if (!t.isEmpty()) {
                sb.append(t).append("\n\n");
            }
        }

        // Fallback: if no block elements matched, use root.text()
        String raw = !sb.isEmpty() ? sb.toString() : root.text();

        return raw.replaceAll("[^\\S\\n]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[\\u00AD\\u200B\\u200C\\u200D\\uFEFF]", "")
                .replaceAll("[\\u2018\\u2019]", "'")
                .replaceAll("[\\u201C\\u201D]", "\"")
                .replaceAll("[\\u2013\\u2014]", "-")
                .trim();
    }
}
