package com.expensetracker.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.expensetracker.importer.StatementTextParser.ParsedRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @Test
    void readsEntriesWithLeadingSignsAndContinuationLines() {
        List<ParsedRow> rows = parse("""
                Sparkasse Musterstadt Kontoauszug 11/2026 Seite 1 von 2
                IBAN DE12 3456 7890 1234 5678 90
                Kontostand am 31.10.2026 1.234,56 +
                Buchungstag Wert Erläuterung Betrag
                03.11.2026 03.11.2026 Lastschrift REWE Markt GmbH -45,50
                Kartenzahlung girocard 02.11 12:34
                Seite 1 von 2
                05.11.2026 05.11.2026 Überweisung ACME GmbH +3.000,00
                Gehalt November
                Kontostand am 30.11.2026 4.189,06 +
                """);

        assertThat(rows).containsExactly(
                new ParsedRow(LocalDate.of(2026, 11, 3), new BigDecimal("-45.50"),
                        "Lastschrift REWE Markt GmbH Kartenzahlung girocard 02.11 12:34", true),
                new ParsedRow(LocalDate.of(2026, 11, 5), new BigDecimal("3000.00"),
                        "Überweisung ACME GmbH Gehalt November", true));
    }

    @Test
    void readsDebitCreditMarkersAndDatesWithoutYearAcrossNewYear() {
        List<ParsedRow> rows = parse("""
                Kontoauszug Nr. 1 vom 05.01.2027
                Bu-Tag Wert Vorgang Soll Haben
                30.12. 30.12. Basislastschrift Stadtwerke 89,00 S
                02.01. 02.01. Lohn/Gehalt ACME 2.500,00 H
                """);

        assertThat(rows).extracting(ParsedRow::date)
                .containsExactly(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2));
        assertThat(rows).extracting(ParsedRow::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-89.00"), new BigDecimal("2500.00"));
        assertThat(rows).allMatch(ParsedRow::signCertain);
    }

    @Test
    void takesTheSignedAmountWhenARunningBalanceFollows() {
        List<ParsedRow> rows = parse("""
                02.11.2026 Lastschrift Netflix 17,99- 1.216,57
                Mandat: M123 Referenz: R456
                04.11.2026 Kartenzahlung Bäckerei Bon 3,20 -4,20 €
                """);

        assertThat(rows).extracting(ParsedRow::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-17.99"), new BigDecimal("-4.20"));
        assertThat(rows.get(0).description()).isEqualTo("Lastschrift Netflix Mandat: M123 Referenz: R456");
        assertThat(rows.get(1).description()).isEqualTo("Kartenzahlung Bäckerei Bon 3,20");
    }

    @Test
    void guessesUnsignedAmountsAndMarksThemUncertain() {
        List<ParsedRow> rows = parse("""
                06.11.2026 Kartenzahlung Tankstelle 60,00
                07.11.2026 Gutschrift Erstattung Versicherung 120,00
                """);

        assertThat(rows).extracting(ParsedRow::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-60.00"), new BigDecimal("120.00"));
        assertThat(rows).noneMatch(ParsedRow::signCertain);
    }

    @Test
    void ignoresBalancesHeadersAndLinesWithoutAmount() {
        List<ParsedRow> rows = parse("""
                01.11.2026 Alter Kontostand 1.000,00
                01.11.2026 Kontoauszug erstellt
                15.11.2026 Zwischensumme -500,00
                31.11.2026 Ungültiges Datum -5,00
                """);

        assertThat(rows).isEmpty();
    }

    @Test
    void removesValueDateNotesFromDescription() {
        List<ParsedRow> rows = parse("""
                10.11.2026 Dauerauftrag Miete Wert: 10.11.2026 -900,00
                """);

        assertThat(rows).singleElement().satisfies(r -> assertThat(r.description()).isEqualTo("Dauerauftrag Miete"));
    }

    private static List<ParsedRow> parse(String text) {
        return StatementTextParser.parse(text.lines().toList(), TODAY);
    }
}
