package com.one.actscraper;

import java.util.Date;

public class Mention {

    private String title;
    private String url;
    private Date publishedDate;
    private String sourceName;
    private double sourceWeight;
    private boolean flagged;

    public Mention(String title, String url, Date publishedDate, String sourceName, double sourceWeight) {
        this.title = title;
        this.url = url;
        this.publishedDate = publishedDate;
        this.sourceName = sourceName;
        this.sourceWeight = sourceWeight;
        this.flagged = false;
    }

    public String getUrl() {
        return url;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public boolean isFlagged() {
        return flagged;
    }

    @Override
    public String toString() {
        return (flagged ? "[FLAGGED] " : "") + "[" + sourceName + "] " + title + " (" + publishedDate + ")";
    }
}