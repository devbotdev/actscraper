package com.one.actscraper.Gemini;

import com.one.actscraper.ActscraperApplication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for managing search date ranges.
 * Allows updating start and end dates dynamically through the UI.
 */
@RestController
@RequestMapping("/api/dates")
public class DateController {

    /**
     * Update the start and end dates for filtering mentions
     * @param startDate Start date (format: yyyy-MM-dd)
     * @param endDate End date (format: yyyy-MM-dd)
     * @return Confirmation response with updated dates
     */
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateDates(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        try {
            // Convert LocalDate to Date
            Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

            // Validate that start date is before end date
            if (start.after(end)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Start date must be before end date");
                return ResponseEntity.badRequest().body(error);
            }

            // Update dates in ActscraperApplication
            ActscraperApplication.setStartDate(start);
            ActscraperApplication.setEndDate(end);

            // Clear out-of-date URLs so they can be re-evaluated with the new date range
            ActscraperApplication.getActscraperApplication().getOutOfDateUrls().clear();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Dates updated successfully");
            response.put("startDate", formatDate(start));
            response.put("endDate", formatDate(end));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error updating dates: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Get the current date range
     * @return Current start and end dates
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentDates() {
        Map<String, Object> response = new HashMap<>();
        response.put("startDate", formatDate(ActscraperApplication.getStartDate()));
        response.put("endDate", formatDate(ActscraperApplication.getEndDate()));
        return ResponseEntity.ok(response);
    }

    private String formatDate(Date date) {
        java.time.LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return localDate.toString(); // Returns yyyy-MM-dd format
    }
}


