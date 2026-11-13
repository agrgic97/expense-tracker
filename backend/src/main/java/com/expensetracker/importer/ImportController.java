package com.expensetracker.importer;

import com.expensetracker.importer.ImportDtos.ImportRequest;
import com.expensetracker.importer.ImportDtos.ImportResult;
import com.expensetracker.importer.ImportDtos.PreviewRequest;
import com.expensetracker.importer.ImportDtos.PreviewResponse;
import com.expensetracker.importer.ImportDtos.StatementsResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importing bank statements: PDFs are read here, CSV files in the browser. For the rows of both the server suggests
 * categories and flags rows that are booked already, then stores the rows the user confirmed.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StatementsResponse statements(@RequestParam("files") List<MultipartFile> files) {
        return new StatementsResponse(service.readStatements(files));
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
