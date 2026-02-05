package dev.project.translating;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.*;
import net.sf.extjwnl.dictionary.Dictionary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WordNetTranslationChecker {
    private Dictionary dictionary;

    public WordNetTranslationChecker() {
        try {
            this.dictionary = Dictionary.getDefaultResourceInstance();
            System.out.println("WordNet инициализирован успешно");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации WordNet: " + e.getMessage(), e);
        }
    }

    public boolean areSynonyms(String word1, String word2) {
        if (word1 == null || word2 == null || word1.equalsIgnoreCase(word2)) {
            return true;
        }

        try {
            String cleanWord1 = word1.toLowerCase().trim();
            String cleanWord2 = word2.toLowerCase().trim();

            IndexWordSet word1Set = dictionary.lookupAllIndexWords(cleanWord1);
            IndexWordSet word2Set = dictionary.lookupAllIndexWords(cleanWord2);

            if (word1Set.size() == 0 || word2Set.size() == 0) {
                return false;
            }

            for (POS pos : POS.getAllPOS()) {
                IndexWord iw1 = word1Set.getIndexWord(pos);
                IndexWord iw2 = word2Set.getIndexWord(pos);

                if (iw1 != null && iw2 != null) {
                    if (areWordsInSameSynset(iw1, iw2)) {
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Ошибка при проверке слов: " + e.getMessage());
            return false;
        }
    }

    private boolean areWordsInSameSynset(IndexWord word1, IndexWord word2) throws JWNLException {
        List<Synset> senses1 = word1.getSenses();
        List<Synset> senses2 = word2.getSenses();

        for (Synset synset1 : senses1) {
            for (Synset synset2 : senses2) {
                if (synset1.equals(synset2)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<String> getAllSynonyms(String word) {
        Set<String> synonyms = new HashSet<>();

        try {
            IndexWordSet wordSet = dictionary.lookupAllIndexWords(word.toLowerCase());

            for (POS pos : POS.getAllPOS()) {
                IndexWord indexWord = wordSet.getIndexWord(pos);
                if (indexWord != null) {
                    for (Synset synset : indexWord.getSenses()) {
                        for (Word w : synset.getWords()) {
                            if (!w.getLemma().equalsIgnoreCase(word)) {
                                synonyms.add(w.getLemma());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при получении синонимов: " + e.getMessage());
        }

        return synonyms;
    }

    public boolean areRelatedWords(String word1, String word2) {
        if (areSynonyms(word1, word2)) {
            return true;
        }
        Set<String> synonyms1 = getAllSynonyms(word1);
        Set<String> synonyms2 = getAllSynonyms(word2);

        for (String syn : synonyms1) {
            if (synonyms2.contains(syn)) {
                return true;
            }
        }

        return false;
    }

    public boolean areWordsSemanticallyClose(String word1, String word2) {
        if (word1 == null || word2 == null) return false;

        String clean1 = word1.toLowerCase().trim();
        String clean2 = word2.toLowerCase().trim();

        if (clean1.equals(clean2)) return true;

        if (areSynonyms(clean1, clean2)) return true;

        try {
            if (areWordsRelated(clean1, clean2)) return true;
        } catch (JWNLException e) {
            throw new RuntimeException(e);
        }

        return haveCommonSynonyms(clean1, clean2);
    }

    private boolean areWordsRelated(String word1, String word2) throws JWNLException {
        IndexWordSet word1Set = dictionary.lookupAllIndexWords(word1);
        IndexWordSet word2Set = dictionary.lookupAllIndexWords(word2);

        for (POS pos : POS.getAllPOS()) {
            IndexWord iw1 = word1Set.getIndexWord(pos);
            IndexWord iw2 = word2Set.getIndexWord(pos);

            if (iw1 != null && iw2 != null) {
                if (checkHypernymsHyponyms(iw1, iw2) || checkHypernymsHyponyms(iw2, iw1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkHypernymsHyponyms(IndexWord word1, IndexWord word2) throws JWNLException {
        for (Synset synset1 : word1.getSenses()) {
            for (PointerTarget hypernym : synset1.getTargets(PointerType.HYPERNYM)) {
                if (hypernym instanceof Synset) {
                    Synset hypernymSynset = (Synset) hypernym;
                    for (Word w : hypernymSynset.getWords()) {
                        if (w.getLemma().equalsIgnoreCase(word2.getLemma())) {
                            return true;
                        }
                    }
                }
            }

            for (PointerTarget hyponym : synset1.getTargets(PointerType.HYPONYM)) {
                if (hyponym instanceof Synset) {
                    Synset hyponymSynset = (Synset) hyponym;
                    for (Word w : hyponymSynset.getWords()) {
                        if (w.getLemma().equalsIgnoreCase(word2.getLemma())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public String getWordDefinition(String word, String posFilter) {
        try {
            IndexWordSet wordSet = dictionary.lookupAllIndexWords(word.toLowerCase());
            StringBuilder definitions = new StringBuilder();

            for (POS pos : POS.getAllPOS()) {
                if (posFilter != null && !pos.getLabel().equalsIgnoreCase(posFilter)) {
                    continue;
                }

                IndexWord indexWord = wordSet.getIndexWord(pos);
                if (indexWord != null) {
                    definitions.append("[").append(pos.getLabel()).append("]: ");

                    List<Synset> senses = indexWord.getSenses();
                    for (int i = 0; i < Math.min(senses.size(), 3); i++) {
                        Synset synset = senses.get(i);
                        definitions.append(synset.getGloss())
                                .append(i < Math.min(senses.size(), 3) - 1 ? "; " : "");
                    }
                    definitions.append("\n");
                }
            }

            return definitions.length() > 0 ? definitions.toString() : "Определение не найдено";

        } catch (Exception e) {
            System.err.println("Ошибка при получении определения: " + e.getMessage());
            return "Ошибка при получении определения";
        }
    }

    private boolean haveCommonSynonyms(String word1, String word2) {
        Set<String> synonyms1 = getAllSynonyms(word1);
        Set<String> synonyms2 = getAllSynonyms(word2);

        long commonCount = synonyms1.stream()
                .filter(synonyms2::contains)
                .count();

        return commonCount > 0;
    }

}