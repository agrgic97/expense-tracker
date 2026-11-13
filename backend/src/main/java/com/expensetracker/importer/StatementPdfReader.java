package com.expensetracker.importer;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts the text lines of a PDF, ordered as they appear on the page. */
@Component
public class StatementPdfReader {

    static final int MAX_PAGES = 100;

    public List<String> lines(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new IllegalArgumentException("Die PDF hat mehr als " + MAX_PAGES + " Seiten");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // Table cells of one row end up on one line, as on the printed page
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text.isBlank()) {
                throw new IllegalArgumentException("Die PDF enthält keinen Text (eingescannt?) und kann nicht gelesen werden");
            }
            return text.lines().toList();
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Die PDF ist passwortgeschützt");
        } catch (IOException e) {
            throw new IllegalArgumentException("Die Datei ist keine lesbare PDF");
        }
    }
}
