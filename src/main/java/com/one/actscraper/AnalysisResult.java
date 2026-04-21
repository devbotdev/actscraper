package com.one.actscraper;

import com.one.actscraper.Item.FeedbackTone;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * Represents a single analysis result from Gemini with sentiment explanation.
 * Suitable for JSON storage and Thymeleaf rendering.
 */
public class AnalysisResult {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Date publishedDate;

    private String title;
    private String url;
    private String sourceName;
    private double sourceWeight;
    private boolean flagged;
    private String feedbackTone;
    private String appraisal;

    // Default constructor for Jackson deserialization
    public AnalysisResult() {
        this.title = "";
        this.url = "";
        this.sourceName = "";
        this.sourceWeight = 0;
        this.flagged = false;
        this.feedbackTone = "UNKNOWN";
        this.appraisal = "";
        this.publishedDate = new Date();
    }

    public AnalysisResult(Mention mention, String appraisal) {
        this.title = mention.getTitle();
        this.url = mention.getUrl();
        this.publishedDate = mention.getPublishedDate();
        this.sourceName = mention.getSourceName();
        this.sourceWeight = mention.getSourceWeight();
        this.flagged = mention.isFlagged();
        this.feedbackTone = mention.getFeedbackTone().toString();
        this.appraisal = appraisal;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Date getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(Date publishedDate) {
        this.publishedDate = publishedDate;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public double getSourceWeight() {
        return sourceWeight;
    }

    public void setSourceWeight(double sourceWeight) {
        this.sourceWeight = sourceWeight;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public String getFeedbackTone() {
        return feedbackTone;
    }

    public void setFeedbackTone(String feedbackTone) {
        this.feedbackTone = feedbackTone;
    }

    public String getAppraisal() {
        return appraisal;
    }

    public void setAppraisal(String appraisal) {
        this.appraisal = appraisal;
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", sourceName='" + sourceName + '\'' +
                ", feedbackTone='" + feedbackTone + '\'' +
                ", flagged=" + flagged +
                ", appraisal='" + appraisal + '\'' +
                '}';
    }
}