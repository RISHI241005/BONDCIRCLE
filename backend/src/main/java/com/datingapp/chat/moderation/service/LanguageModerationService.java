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
    private static final Map<String, String> PHONETIC_TERMS = buildPhoneticTerms();
    private static final Set<String> SAFE_WORDS = Set.of(
            "assistant", "assistants", "assistance", "class", "classes", "classic",
            "assignment", "assignments", "assess", "assessment", "passion", "passionate",
            "compass", "sheet", "sheets", "batch", "batches", "beach", "beaches",
            "witch", "witches", "pitch", "pitches", "ditch", "stitch", "kitchen",
            "where", "wherever", "whole", "wholes", "wholesale", "randy"
    );

    public ModerationResult analyze(String content) {
        if (content == null || content.isBlank()) {
            return new ModerationResult(false, List.of());
        }

        List<String> tokens = tokenize(content);
        Set<String> matches = new LinkedHashSet<>();
        for (String token : tokens) {
            String label = findMatch(token);
            if (label != null) {
                matches.add(label);
            }
        }

        // Some South-Asian insults are commonly typed either as one word or
        // as a two/three-word phrase. Check compact n-grams so spellings such
        // as "kuttar baccha" and "khankir pola" are covered without making
        // their harmless component words globally unsafe.
        for (int start = 0; start < tokens.size(); start++) {
            StringBuilder phrase = new StringBuilder(tokens.get(start));
            for (int end = start + 1; end < Math.min(tokens.size(), start + 3); end++) {
                phrase.append(tokens.get(end));
                String label = findPhraseMatch(phrase.toString());
                if (label != null) {
                    matches.add(label);
                }
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
        String label = findMatch(value);
        if (label != null) {
            matches.add(label);
        }
    }

    private static String findMatch(String token) {
        String compact = collapseRepeats(token);
        String label = TERMS.get(token);
        if (label == null) {
            label = TERMS.get(compact);
        }
        if (label != null) {
            return label;
        }
        if (SAFE_WORDS.contains(token) || SAFE_WORDS.contains(compact)) {
            return null;
        }

        String phonetic = phoneticKey(token);
        label = PHONETIC_TERMS.get(phonetic);
        if (label != null) {
            return label;
        }

        // A conservative typo comparison catches pronunciation spellings that
        // the phonetic key does not cover. Short words are excluded because a
        // one-character difference is too likely to be an innocent word.
        if (compact.length() < 5 || compact.length() > 40) {
            return null;
        }
        for (Map.Entry<String, String> entry : TERMS.entrySet()) {
            String candidate = entry.getKey();
            int longest = Math.max(compact.length(), candidate.length());
            int allowedDistance = longest >= 9 ? 2 : 1;
            if (candidate.length() < 5
                    || Math.abs(compact.length() - candidate.length()) > allowedDistance) {
                continue;
            }

            String candidatePhonetic = phoneticKey(candidate);
            if (phonetic.isEmpty() || candidatePhonetic.isEmpty()
                    || phonetic.charAt(0) != candidatePhonetic.charAt(0)) {
                continue;
            }
            if (damerauLevenshtein(compact, candidate, allowedDistance) <= allowedDistance
                    || damerauLevenshtein(phonetic, candidatePhonetic, allowedDistance) <= allowedDistance) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String findPhraseMatch(String compactPhrase) {
        String compact = collapseRepeats(compactPhrase);
        String label = TERMS.get(compactPhrase);
        if (label == null) {
            label = TERMS.get(compact);
        }
        if (label != null) {
            return label;
        }
        return PHONETIC_TERMS.get(phoneticKey(compactPhrase));
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

    private static String phoneticKey(String value) {
        String key = value
                .replace("ph", "f")
                .replace("qu", "k")
                .replace("ck", "k")
                .replace("kh", "k")
                .replace("gh", "g")
                .replace("ee", "i")
                .replace("ea", "i")
                .replace("oo", "u")
                .replace("ou", "u")
                .replace('q', 'k')
                .replace('c', 'k')
                .replace('w', 'v')
                .replace('y', 'i');
        return collapseRepeats(key);
    }

    private static int damerauLevenshtein(String left, String right, int limit) {
        if (Math.abs(left.length() - right.length()) > limit) {
            return limit + 1;
        }
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) distance[i][0] = i;
        for (int j = 0; j <= right.length(); j++) distance[0][j] = j;

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                int best = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + substitutionCost);
                if (i > 1 && j > 1
                        && left.charAt(i - 1) == right.charAt(j - 2)
                        && left.charAt(i - 2) == right.charAt(j - 1)) {
                    best = Math.min(best, distance[i - 2][j - 2] + 1);
                }
                distance[i][j] = best;
            }
        }
        return distance[left.length()][right.length()];
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
        addTerms(terms, "cunt", "cunt", "cunts");
        addTerms(terms, "prick", "prick", "pricks");
        addTerms(terms, "wanker", "wanker", "wankers");
        addTerms(terms, "scumbag", "scumbag", "scumbags");
        addTerms(terms, "jackass", "jackass", "jackasses");
        addTerms(terms, "dipshit", "dipshit", "dipshits");
        addTerms(terms, "retard", "retard", "retarded");

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
        addTerms(terms, "bhosdike", "bhosdike", "bhosdk", "bhosadike", "bhosdiwale");
        addTerms(terms, "buddhu", "buddhu", "budhu", "buddhoo");
        addTerms(terms, "nalayak", "nalayak", "nalaayak", "nalayiq");
        addTerms(terms, "nikamma", "nikamma", "nikamme", "nikammi");
        addTerms(terms, "ullu", "ullu", "ulloo");
        addTerms(terms, "jahil", "jahil", "jaahil");
        addTerms(terms, "ghatiya", "ghatiya", "ghatiyaa");
        addTerms(terms, "chapri", "chapri", "chhapri");
        addTerms(terms, "chhinal", "chhinal", "chinaal");

        // Broader Hindi/Hinglish internet slang and common transliterations.
        addTerms(terms, "bevda", "bevda", "bewda", "bevde", "bewde", "bevdey", "bewday");
        addTerms(terms, "bhadwa", "bhadwa", "bhadvaa", "bhadva", "bhadua", "bhaduaa");
        addTerms(terms, "bhosda", "bhosda", "bhosada", "bhosdaa", "bhonsda");
        addTerms(terms, "bhosdiki", "bhosdiki", "bhosadiki", "bhosdikiwala", "bhosdikiwale");
        addTerms(terms, "bsdk", "bsdk", "bhsdk", "bhosdk");
        addTerms(terms, "charsi", "charsi", "charsii");
        addTerms(terms, "chutad", "chutad", "chuttad", "chuttar");
        addTerms(terms, "fattu", "fattu", "fattuu", "fatoo");
        addTerms(terms, "gaand", "gaand", "gand", "gaandu", "gandiya", "gandiye");
        addTerms(terms, "gandfat", "gandfat", "gaandfat", "gandfut", "gaandfut");
        addTerms(terms, "gadhalund", "gadhalund", "gadhekalund");
        addTerms(terms, "haramzada", "haramzada", "haramjada", "haraamzada", "haraamjada",
                "haramzade", "haramjade", "haraamzade", "haraamjade");
        addTerms(terms, "haramkhor", "haramkhor", "haraamkhor", "haramkhore");
        addTerms(terms, "jhatu", "jhatu", "jhaatu", "jhatoo");
        addTerms(terms, "kuttiya", "kutia", "kutiya", "kuttiya", "kuttiyaa");
        addTerms(terms, "lauda", "lauda", "laudey", "laura", "lora", "lode");
        addTerms(terms, "lulli", "lulli", "lully", "lullii");
        addTerms(terms, "nunnu", "nunnu", "nunni", "nunoo");
        addTerms(terms, "raand", "raand", "rand");
        addTerms(terms, "suar", "suar", "suwar", "sooar", "suarni");
        addTerms(terms, "tatti", "tatti", "tatty", "tattie");
        addTerms(terms, "tatte", "tatte", "tattey", "tattee");
        addTerms(terms, "fuddu", "fuddu", "fuddoo");
        addTerms(terms, "chomu", "chomu", "chomoo", "chomuu");
        addTerms(terms, "dhakkan", "dhakkan", "dhakan");
        addTerms(terms, "ghonchu", "ghonchu", "ghonchoo", "ghonchuu");
        addTerms(terms, "baklol", "baklol", "bakloll");
        addTerms(terms, "nibba", "nibba", "nibbi", "nibbe", "nibbaa");

        // Bengali/Banglish abuse frequently mixed into Romanized Indian chat.
        addTerms(terms, "bokachoda", "bokachoda", "bokachodha", "bokachod",
                "bokchod", "bokaachoda", "bokaachod");
        addTerms(terms, "bokachodi", "bokachodi", "bokachudi", "bokachody", "bokachudii");
        addTerms(terms, "abalchoda", "abalchoda", "abalchod", "abalchudha");
        addTerms(terms, "paglachoda", "paglachoda", "paglachod", "paglachudha");
        addTerms(terms, "paglichudi", "paglichudi", "paglichodi", "paglichudii");
        addTerms(terms, "khanki", "khanki", "khaanki", "khankee");
        addTerms(terms, "khankirpola", "khankirpola", "khankirpoa");
        addTerms(terms, "magirpola", "magirpola", "magirpoa");
        addTerms(terms, "chutmarani", "chutmarani", "chudmarani",
                "chootmarani", "choodmarani");
        addTerms(terms, "shuorerbaccha", "shuorerbaccha", "suorerbaccha", "shuorerbacha",
                "suorerbacha", "shuorerbachcha");
        addTerms(terms, "kuttarbacha", "kuttarbacha", "kuttarbaccha", "kuttarbachcha");
        addTerms(terms, "bhodai", "bhodai", "vodai");
        addTerms(terms, "balchoda", "balchoda", "balchod", "baalchoda");
        addTerms(terms, "balmarka", "balmarka", "baalmarka");
        addTerms(terms, "bhogchod", "bhogchod", "bhogchoda", "vogchod");
        addTerms(terms, "murkhochoda", "murkhochoda", "murkhochod");
        addTerms(terms, "bolodchoda", "bolodchoda", "bolodchod", "balodchoda");
        addTerms(terms, "beyadob", "beyadob", "beadob", "beyadab");
        addTerms(terms, "janowar", "janowar", "janoar", "janwar");
        addTerms(terms, "shala", "shala", "shalaa");
        return Map.copyOf(terms);
    }

    private static Map<String, String> buildPhoneticTerms() {
        Map<String, String> phoneticTerms = new LinkedHashMap<>();
        TERMS.forEach((variant, label) -> phoneticTerms.putIfAbsent(phoneticKey(variant), label));
        return Map.copyOf(phoneticTerms);
    }

    private static void addTerms(Map<String, String> terms, String label, String... variants) {
        for (String variant : variants) {
            terms.put(variant, label);
            terms.put(collapseRepeats(variant), label);
        }
    }
}
