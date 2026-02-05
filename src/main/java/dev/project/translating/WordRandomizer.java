package dev.project.translating;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class WordRandomizer {
    private static final List<String> words = new ArrayList<>();

    public WordRandomizer() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream("text_files/Words.txt")));
        String line;
        try {
            while (((line = reader.readLine()) != null)) {
                String s[] = line.split(" ");
                words.add(s[s.length-1]);
            }
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getNextWord(int currentID) {
        return words.get(currentID);
    }
}