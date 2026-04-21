package com.one.actscraper.Gemini;

import com.one.actscraper.AnalysisResult;
import com.one.actscraper.FileStorage;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Controller for displaying analysis results via Thymeleaf templates.
 * Makes JSON analysis data available to the web frontend for visualization and graphs.
 */
@Controller
@RequestMapping("/analysis")
public class AnalysisController {

    /**
     * Get all analysis results
     */
    @GetMapping("/results")
    public String getResults(Model model) {
        List<AnalysisResult> results = FileStorage.loadRelevantAnalysisResultsFromJson();
        FileStorage.AnalysisStatistics stats = FileStorage.getRelevantStatistics();

        model.addAttribute("results", results);
        model.addAttribute("stats", stats);
        model.addAttribute("totalResults", results.size());

        return "analysis-results";
    }

    /**
     * Get positive feedback results
     */
    @GetMapping("/positive")
    public String getPositiveResults(Model model) {
        List<AnalysisResult> results = FileStorage.loadRelevantAnalysisResultsFromJson()
                .stream()
                .filter(r -> "POSITIVE".equals(r.getFeedbackTone()))
                .toList();

        model.addAttribute("results", results);
        model.addAttribute("sentiment", "POSITIVE");
        model.addAttribute("count", results.size());

        return "sentiment-filtered";
    }

    /**
     * Get negative feedback results
     */
    @GetMapping("/negative")
    public String getNegativeResults(Model model) {
        List<AnalysisResult> results = FileStorage.loadRelevantAnalysisResultsFromJson()
                .stream()
                .filter(r -> "NEGATIVE".equals(r.getFeedbackTone()))
                .toList();

        model.addAttribute("results", results);
        model.addAttribute("sentiment", "NEGATIVE");
        model.addAttribute("count", results.size());

        return "sentiment-filtered";
    }

    /**
     * Get statistics dashboard
     */
    @GetMapping("/dashboard")
    public String getDashboard(Model model) {
        FileStorage.AnalysisStatistics stats = FileStorage.getRelevantStatistics();
        List<AnalysisResult> results = FileStorage.loadRelevantAnalysisResultsFromJson();

        model.addAttribute("stats", stats);
        model.addAttribute("results", results);

        return "dashboard";
    }

    /**
     * Get flagged results (relevant mentions)
     */
    @GetMapping("/flagged")
    public String getFlaggedResults(Model model) {
        List<AnalysisResult> results = FileStorage.loadRelevantAnalysisResultsFromJson();

        model.addAttribute("results", results);
        model.addAttribute("count", results.size());

        return "flagged-results";
    }
}

