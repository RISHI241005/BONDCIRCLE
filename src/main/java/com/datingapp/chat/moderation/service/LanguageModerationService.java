package com.datingapp.chat.moderation.service;

import com.datingapp.chat.moderation.dto.ModerationResult;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, deterministic guard for common English and Romanized Hindi
 * abusive terms. The original message is never logged or stored by this service.
 */
@Service
public class LanguageModerationService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final Pattern REPEATED_CHARACTER = Pattern.compile("(.)\\1+");
    private static final Map<Character, Character> LEET_EQUIVALENTS = Map.of(
            '0', 'o',
            '1', 'i',
            '3', 'e',
            '4', 'a',
            '5', 's',
            '7', 't',
            '@', 'a',
            '$', 's'
    );

    private static final Map<String, String> TERMS = buildTerms();

    public ModerationResult analyze(String content) {
        if (content == null || content.isBlank()) {
            return new ModerationResult(false, List.of());
        }

        List<String> tokens = tokenize(content);
        Set<String> matches = new LinkedHashSet<>();
        for (String token : tokens) {
            String label = TERMS.get(token);
            if (label == null) {
                label = TERMS.get(collapseRepeats(token));
            }
            if (label != null) {
                matches.add(label);
            }
        }

        // Catch deliberately separated spellings such as "f.u.c.k" without
        // joining ordinary multi-word sentences.
        StringBuilder separated = new StringBuilder();
        for (String token : tokens) {
            if (token.length() == 1) {
                separated.append(token);
            } else {
                collectSeparatedMatch(separated, matches);
                separated.setLength(0);
            }
        }
        collectSeparatedMatch(separated, matches);

        return new ModerationResult(!matches.isEmpty(), new ArrayList<>(matches));
    }

    private static void collectSeparatedMatch(StringBuilder separated, Set<String> matches) {
        if (separated.length() < 3) {
            return;
        }
        String value = separated.toString();
        String label = TERMS.get(value);
        if (label == null) {
            label = TERMS.get(collapseRepeats(value));
        }
        if (label != null) {
            matches.add(label);
        }
    }

    private static List<String> tokenize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder translated = new StringBuilder(normalized.length());
        for (char character : normalized.toCharArray()) {
            translated.append(LEET_EQUIVALENTS.getOrDefault(character, character));
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(translated);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static String collapseRepeats(String value) {
        return REPEATED_CHARACTER.matcher(value).replaceAll("$1");
    }

    private static Map<String, String> buildTerms() {
        Map<String, String> terms = new LinkedHashMap<>();
        addTerms(terms, "idiot", "idiot", "idiots");
        addTerms(terms, "stupid", "stupid", "stupidity");
        addTerms(terms, "moron", "moron", "morons");
        addTerms(terms, "dumbass", "dumbass", "dumbasses");
        addTerms(terms, "asshole", "asshole", "assholes");
        addTerms(terms, "bastard", "bastard", "bastards");
        addTerms(terms, "bitch", "bitch", "bitches", "bitchy");
        addTerms(terms, "fuck", "fuck", "fucker", "fuckers", "fucking", "fucked");
        addTerms(terms, "shit", "shit", "shitty", "bullshit");
        addTerms(terms, "whore", "whore", "whores");
        addTerms(terms, "slut", "slut", "sluts");
        addTerms(terms, "dickhead", "dickhead", "dickheads");
        addTerms(terms, "motherfucker", "motherfucker", "motherfuckers");

        addTerms(terms, "bewakoof", "bewakoof", "bewaqoof", "bewaquf", "bevakoof");
        addTerms(terms, "pagal", "pagal", "paagal");
        addTerms(terms, "gadha", "gadha", "gadhae", "gadhe", "gadhi");
        addTerms(terms, "kamina", "kamina", "kamine", "kamini", "kameena", "kameene");
        addTerms(terms, "harami", "harami", "haraami");
        addTerms(terms, "kutta", "kutta", "kutte", "kutti");
        addTerms(terms, "saala", "saala", "sala", "saale");
        addTerms(terms, "bakchod", "bakchod", "bakchodi");
        addTerms(terms, "chutiya", "chutiya", "chutia", "chutiye", "chutiyo");
        addTerms(terms, "madarchod", "madarchod", "madarchod", "motherchod");
        addTerms(terms, "behenchod", "behenchod", "bhenchod", "benchod");
        addTerms(terms, "randi", "randi", "randii");
        addTerms(terms, "laude", "laude", "lavde", "lodu", "loda");
        addTerms(terms, "lund", "lund", "lundu");
        addTerms(terms, "gandu", "gandu", "gaandu");
        return Map.copyOf(terms);
    }

    private static void addTerms(Map<String, String> terms, String label, String... variants) {
        for (String variant : variants) {
            terms.put(variant, label);
            terms.put(collapseRepeats(variant), label);
        }
    }
}
