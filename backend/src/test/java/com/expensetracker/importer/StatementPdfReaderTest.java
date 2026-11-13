package com.expensetracker.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.expensetracker.auth.CurrentUser;
import com.expensetracker.category.CategoryService;
import com.expensetracker.expense.ExpenseRepository;
import com.expensetracker.importer.ImportDtos.StatementFile;
import com.expensetracker.rule.CategoryRuleService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class StatementPdfReaderTest {

    private final StatementPdfReader reader = new StatementPdfReader();

    @Test
    void readsTableRowsAsLines() throws IOException {
        // Columns are drawn separately, as statement generators do; they must come back as one line per row
        byte[] pdf = pdf(new String[][] {
                {"Buchungstag", "Erläuterung", "Betrag"},
                {"03.11.2026", "Lastschrift Bäckerei Müller", "-4,20 €"},
                {"05.11.2026", "Gutschrift ACME GmbH", "+3.000,00 €"},
        });

        List<String> lines = reader.lines(pdf);

        assertThat(lines).anyMatch(l -> l.contains("03.11.2026") && l.contains("Bäckerei Müller") && l.contains("-4,20 €"));
        var rows = StatementTextParser.parse(lines, java.time.LocalDate.of(2026, 11, 13));
        assertThat(rows).extracting(StatementTextParser.ParsedRow::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-4.20"), new BigDecimal("3000.00"));
    }

    @Test
    void rejectsFilesThatAreNoPdf() {
        assertThatThrownBy(() -> reader.lines("kein pdf".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keine lesbare PDF");
    }

    @Test
    void reportsEachFileSeparately() throws IOException {
        ImportService service = new ImportService(mock(ExpenseRepository.class), mock(CategoryService.class),
                mock(CategoryRuleService.class), mock(CurrentUser.class), reader, Clock.systemDefaultZone());
        byte[] good = pdf(new String[][] {{"03.11.2026", "Lastschrift Netflix", "-17,99"}});
        byte[] empty = pdf(new String[][] {{"Sehr geehrte Kundin,", "", ""}});

        List<StatementFile> files = service.readStatements(List.of(
                new MockMultipartFile("files", "november.pdf", "application/pdf", good),
                new MockMultipartFile("files", "brief.pdf", "application/pdf", empty),
                new MockMultipartFile("files", "notiz.txt", "application/pdf", "hallo".getBytes())));

        assertThat(files.get(0).rows()).hasSize(1);
        assertThat(files.get(0).error()).isNull();
        assertThat(files.get(1).error()).contains("Keine Buchungen erkannt");
        assertThat(files.get(2).error()).isEqualTo("Keine PDF-Datei");
    }

    private static byte[] pdf(String[][] rows) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float[] columns = {50, 140, 430};
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 700;
                for (String[] row : rows) {
                    for (int c = 0; c < row.length; c++) {
                        if (row[c].isEmpty()) {
                            continue;
                        }
                        content.beginText();
                        content.setFont(font, 10);
                        content.newLineAtOffset(columns[c], y);
                        content.showText(row[c]);
                        content.endText();
                    }
                    y -= 16;
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
