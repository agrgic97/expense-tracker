package com.expensetracker.importer;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the entries in the text of a bank statement. There is no standard layout, so this relies on what German
 * statements have in common: an entry starts with a line beginning with the booking date (optionally followed by
 * the value date) and ending with the amount; the following lines without date and amount continue its text.
 *
 * <p>The sign is read from a leading or trailing +/-, or from S (Soll) / H (Haben). Statements that put debit and
 * credit into separate columns carry no sign in the text; such entries are guessed and marked as uncertain.
 */
public final class StatementTextParser {

    /** An entry as read from a statement: negative amounts are expenses. */
    public record ParsedRow(LocalDate date, BigDecimal amount, String description, boolean signCertain) {
    }

    private static final String NUMBER = "\\d{1,3}(?:\\.\\d{3})*,\\d{2}";
    private static final String AMOUNT = "([+-]\\s?)?(" + NUMBER + ")(\\s?[+-]|\\s[SH](?=\\s|$))?";
    private static final String CURRENCY = "(?:\\s*(?:EUR|€))?";
    private static final Pattern AMOUNTS_AT_END = Pattern.compile(
            "\\s" + AMOUNT + CURRENCY + "(?:\\s+" + AMOUNT + CURRENCY + ")?\\s*$");
    private static final Pattern DATE_AT_START = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})?(?=\\s)");
    private static final Pattern FULL_DATE = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b");
    /** Balance and total lines look like entries but are not */
    private static final Pattern NOT_AN_ENTRY = Pattern.compile(
            "(?i)kontostand|saldo|übertrag|uebertrag|zwischensumme|summe|gesamtumsatz");
    /** Lines between entries that do not belong to an entry's text (page furniture, table headers) */
    private static final Pattern NOISE = Pattern.compile(
            "(?i)^(seite|blatt|kontoauszug|auszug|iban|bic|buchungstag|buchung\\b|datum|valuta|wert\\b|"
                    + "vorgang|betrag|verwendungszweck|erläuterung|übertrag|kontoinhaber|bitte beachten).*");
    private static final Pattern VALUE_DATE_NOTE = Pattern.compile("(?i)\\b(wert|valuta)[: ]\\s*\\d{1,2}\\.\\d{1,2}\\.(\\d{2,4})?");
    private static final Pattern INCOME_HINT = Pattern.compile(
            "(?i)gutschrift|gehalt|lohn|bezüge|eingang|erstattung|rückerstattung|zinsen|dividende|rente|kindergeld");
    private static final int MAX_CONTINUATION_LINES = 3;
    private static final int MAX_DESCRIPTION = 255;

    private StatementTextParser() {
    }

    /**
     * @param today used for the year of dates written without one ("01.11."), if the statement shows no full date
     */
    public static List<ParsedRow> parse(List<String> rawLines, LocalDate today) {
        List<String> lines = rawLines.stream().map(StatementTextParser::normalize).toList();
        LocalDate reference = latestFullDate(lines).orElse(today);

        List<ParsedRow> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Optional<ParsedRow> entry = entry(lines.get(i), reference);
            if (entry.isEmpty()) {
                continue;
            }
            StringBuilder text = new StringBuilder(entry.get().description());
            int added = 0;
            while (added < MAX_CONTINUATION_LINES && i + 1 < lines.size() && isContinuation(lines.get(i + 1))) {
                text.append(' ').append(lines.get(++i));
                added++;
            }
            ParsedRow row = entry.get();
            String description = clean(text.toString());
            boolean signCertain = row.signCertain();
            BigDecimal amount = row.amount();
            if (!signCertain && INCOME_HINT.matcher(description).find()) {
                amount = amount.abs();
            }
            rows.add(new ParsedRow(row.date(), amount, description, signCertain));
        }
        return rows;
    }

    private static Optional<ParsedRow> entry(String line, LocalDate reference) {
        Matcher dateMatch = DATE_AT_START.matcher(line);
        if (!dateMatch.find()) {
            return Optional.empty();
        }
        Matcher amountMatch = AMOUNTS_AT_END.matcher(line);
        if (!amountMatch.find() || amountMatch.start() < dateMatch.end()) {
            return Optional.empty();
        }
        LocalDate date = date(dateMatch.group(1), dateMatch.group(2), dateMatch.group(3), reference);
        if (date == null) {
            return Optional.empty();
        }
        // Skip the value date that often follows the booking date
        String text = line.substring(dateMatch.end(), amountMatch.start()).trim();
        Matcher valueDate = DATE_AT_START.matcher(text);
        if (valueDate.find()) {
            text = text.substring(valueDate.end()).trim();
        }
        if (text.isEmpty() || NOT_AN_ENTRY.matcher(text).find()) {
            return Optional.empty();
        }
        // Two amounts at the end are usually entry and running balance. Prefer the one with a sign, because a
        // number in the text right before an unsigned amount would otherwise be taken for the entry.
        int group = 1;
        if (amountMatch.group(5) != null && sign(amountMatch.group(1), amountMatch.group(3)) == null
                && sign(amountMatch.group(4), amountMatch.group(6)) != null) {
            group = 4;
            text = (text + " " + amountMatch.group(2)).trim();
        }
        String leading = amountMatch.group(group);
        String trailing = amountMatch.group(group + 2);
        BigDecimal value = new BigDecimal(amountMatch.group(group + 1).replace(".", "").replace(',', '.'));
        Boolean negative = sign(leading, trailing);
        BigDecimal amount = Boolean.FALSE.equals(negative) ? value : value.negate();
        if (amount.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ParsedRow(date, amount, text, negative != null));
    }

    /** True for expenses, false for income, null if the text shows no sign. */
    private static Boolean sign(String leading, String trailing) {
        String marker = ((leading != null ? leading : "") + (trailing != null ? trailing : "")).replace(" ", "");
        if (marker.contains("-") || marker.equals("S")) {
            return true;
        }
        if (marker.contains("+") || marker.equals("H")) {
            return false;
        }
        return null;
    }

    private static boolean isContinuation(String line) {
        return !line.isBlank()
                && !DATE_AT_START.matcher(line).find()
                && !AMOUNTS_AT_END.matcher(line).find()
                && !NOISE.matcher(line).matches()
                && !NOT_AN_ENTRY.matcher(line).find();
    }

    /** Dates without a year belong to the statement's period: the year of the latest full date, or the one before. */
    private static LocalDate date(String day, String month, String year, LocalDate reference) {
        try {
            if (year != null) {
                int y = Integer.parseInt(year);
                return LocalDate.of(y < 100 ? 2000 + y : y, Integer.parseInt(month), Integer.parseInt(day));
            }
            LocalDate candidate = LocalDate.of(reference.getYear(), Integer.parseInt(month), Integer.parseInt(day));
            return candidate.isAfter(reference.plusDays(31)) ? candidate.minusYears(1) : candidate;
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static Optional<LocalDate> latestFullDate(List<String> lines) {
        LocalDate latest = null;
        for (String line : lines) {
            Matcher m = FULL_DATE.matcher(line);
            while (m.find()) {
                LocalDate d = date(m.group(1), m.group(2), m.group(3), LocalDate.MIN);
                if (d != null && (latest == null || d.isAfter(latest))) {
                    latest = d;
                }
            }
        }
        return Optional.ofNullable(latest);
    }

    private static String normalize(String line) {
        return line.replace('−', '-').replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String clean(String text) {
        String cleaned = VALUE_DATE_NOTE.matcher(text).replaceAll("").replaceAll("\\s+", " ").trim();
        return cleaned.length() > MAX_DESCRIPTION ? cleaned.substring(0, MAX_DESCRIPTION).trim() : cleaned;
    }
}
