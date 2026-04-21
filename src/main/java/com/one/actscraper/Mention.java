package com.one.actscraper;

import com.one.actscraper.Item.FeedbackTone;

import java.util.Date;

public class Mention {

    private final String title;
    private final String url;
    private final Date publishedDate;
    private final String sourceName;
    private final double sourceWeight;
    private boolean flagged;
    private FeedbackTone feedbackTone;
    private String appraisal;

    public Mention(String title, String url, Date publishedDate, String sourceName, double sourceWeight) {
        this.title = title;
        this.url = url;
        this.publishedDate = publishedDate;
        this.sourceName = sourceName;
        this.sourceWeight = sourceWeight;
        this.flagged = false;
        this.feedbackTone = FeedbackTone.UNKNOWN;
        this.appraisal = "";
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Date getPublishedDate() {
        return publishedDate;
    }

    public String getSourceName() {
        return sourceName;
    }

    public double getSourceWeight() {
        return sourceWeight;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public FeedbackTone getFeedbackTone() {
        return feedbackTone;
    }

    public void setFeedbackTone(FeedbackTone feedbackTone) {
        this.feedbackTone = feedbackTone == null ? FeedbackTone.UNKNOWN : feedbackTone;
    }

    public String getAppraisal() {
        return appraisal;
    }

    public void setAppraisal(String appraisal) {
        this.appraisal = appraisal == null ? "" : appraisal;
    }

    @Override
    public String toString() {
        String sentimentTag = feedbackTone == FeedbackTone.UNKNOWN ? "" : " [" + feedbackTone + "]";
        return (flagged ? "[FLAGGED] " : "") + "[" + sourceName + "] " + title + sentimentTag + " (" + publishedDate + ")";
    }
}