package com.expensetracker.importer;

import com.expensetracker.importer.ImportDtos.ImportRequest;
import com.expensetracker.importer.ImportDtos.ImportResult;
import com.expensetracker.importer.ImportDtos.PreviewRequest;
import com.expensetracker.importer.ImportDtos.PreviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSV files are parsed in the browser, where the user maps the columns. The server suggests categories and
 * flags rows that are booked already, then stores the rows the user confirmed.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return new PreviewResponse(service.preview(request.rows()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importRows(@Valid @RequestBody ImportRequest request) {
        return new ImportResult(service.importRows(request.rows()));
    }
}
