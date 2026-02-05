package dev.project.translating;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TranslationValidator {
    private SimpleGoogleTranslator translator;
    private WordNetTranslationChecker wordNetChecker;
    
    public TranslationValidator() {
        this.translator = new SimpleGoogleTranslator();
        this.wordNetChecker = new WordNetTranslationChecker();
    }
    
    public boolean validateTranslation(String originalWord, String userTranslation, String fromLang, String toLang) {
            String cleanOriginal = originalWord.toLowerCase().trim();
            String cleanUserTranslation = userTranslation.toLowerCase().trim();
            List<String> synonyms = new ArrayList<>(wordNetChecker.getAllSynonyms(originalWord));
            for (int i = 0; i < synonyms.size(); i++) {
                String word = synonyms.get(i);
                synonyms.remove(word);
                synonyms.add(i, translator.translate(word, toLang, fromLang));
            }
            synonyms.add(translator.translate(cleanOriginal, toLang, fromLang));

            String directTranslation = translator.translate(cleanUserTranslation, fromLang, toLang);
            if (directTranslation.equalsIgnoreCase(cleanOriginal)) {
                return true;
            }

            if (wordNetChecker.areSynonyms(cleanOriginal, directTranslation)) {
                return true;
            }


            if (wordNetChecker.areRelatedWords(cleanOriginal, directTranslation)) {
                return true;
            }

            if (synonyms.contains(cleanUserTranslation)) {
                return true;
            }

            return checkSemanticSimilarity(cleanOriginal, directTranslation);
    }
    
    private boolean checkSemanticSimilarity(String word1, String word2) {
        Set<String> synonyms1 = wordNetChecker.getAllSynonyms(word1);
        Set<String> synonyms2 = wordNetChecker.getAllSynonyms(word2);

        int commonSynonyms = 0;
        for (String syn : synonyms1) {
            if (synonyms2.contains(syn)) {
                commonSynonyms++;
            }
        }
        
        return commonSynonyms >= 2;
    }

    public SimpleGoogleTranslator getTranslator() {
        return translator;
    }
}