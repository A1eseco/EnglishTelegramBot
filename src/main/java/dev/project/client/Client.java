package dev.project.client;

import dev.project.translating.WordRandomizer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class Client {
    private static int MAX_STAGE = 10;
    private long chatID;
    private String firstName;
    private String time;
    private int totalWords = 0;
    private int solvedWords = 0;
    private int dictationWords = 10;
    private int currentSolvedWords = 0;
    private String currentWord;
    private int wordID = 0;
    private int stage = 0;
    private ClientState state;
    private ArrayDeque<String> unsolvedWords = new ArrayDeque<>();
    private ArrayList<String> wordBuffer = new ArrayList<>();
    private boolean flag = false;
    private String dictationMode = "EN_TO_RU";

    public Client(long chatID, String firstName) {
        this.chatID = chatID;
        this.firstName = firstName;
        this.state = ClientState.FREE;
    }

    private Client() {}

    public void save(String dir) {
        try {
            FileWriter writer = new FileWriter(dir + ((Long) chatID).toString());
            writer.write(toJson().toString());
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject toJson() throws IllegalAccessException {
        JSONObject object = new JSONObject();
        Class<?> clazz = this.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.get(this) instanceof ClientState || field.get(this) instanceof ArrayList<?>) continue;
            field.setAccessible(true);
            object.put(field.getName(), field.get(this));
        }
        return object;
    }
    public boolean isDictation() {
        return state == ClientState.DICTATION;
    }

    public String getChatID() {
        return ((Long)chatID).toString();
    }
    public Long getChatIDasLong() {
        return chatID;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getTotalSolvedPercentage() {
        float number = ((float) solvedWords / (totalWords != 0 ? totalWords : 1)) * 100;
        return String.format("%.1f", number);
    }
    public String getSolvedPercentage() {
        float number = ((float) currentSolvedWords / dictationWords) * 100;
        return String.format("%.1f", number);
    }

    public String getCurrentWord() {
        return currentWord;
    }

    public void startDictation() {
        state = ClientState.DICTATION;
        stage++;
        if (!flag) updateWord();
    }

    public void startDictation(String mode) {
        this.dictationMode = mode;
        startDictation();
    }

    public String getDictationMode() {
        return dictationMode;
    }

    public boolean isEnToRu() {
        return "EN_TO_RU".equals(dictationMode);
    }
    public void updateWord() {
        if (!unsolvedWords.isEmpty()) {
            currentWord = unsolvedWords.pop();
        } else {
            wordID++;
            currentWord = WordRandomizer.getNextWord(wordID);
        }
    }
    public void addWord() {
        totalWords++;
    }
    public void addSolvedWord() {
        addWord();
        solvedWords++;
        currentSolvedWords++;
    }

    public void processDictation() {
        stage++;
        updateWord();
    }
    public boolean isDictationEnds() {
        return stage >= MAX_STAGE;
    }

    public void endDictation(String directory) {
        reset();
        save(directory);
        this.state = ClientState.FREE;
        unsolvedWords.addAll(wordBuffer);
        wordBuffer.clear();
    }
    private void reset() {
        stage = 0;
        currentSolvedWords = 0;
    }
    public static Client load(File file) {
        try {
            if (!file.exists()) {
                return null;
            }
            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject jsonObject = new JSONObject(content);

            Client instance = new Client();

            Class<?> clazz = instance.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                if (field.getType().equals(ClientState.class) ||
                        field.getType().equals(WordRandomizer.class)) {
                    continue;
                }

                field.setAccessible(true);

                if (jsonObject.has(field.getName())) {
                    Object value = jsonObject.get(field.getName());

                    if (field.getName().equals("unsolvedWords") && value instanceof JSONArray) {
                        JSONArray jsonArray = (JSONArray) value;
                        ArrayDeque<String> deque = new ArrayDeque<>();

                        for (int i = 0; i < jsonArray.length(); i++) {
                            deque.add(jsonArray.getString(i));
                        }

                        field.set(instance, deque);
                    }
                    else if (field.getName().equals("wordBuffer") && value instanceof JSONArray) {
                        JSONArray jsonArray = (JSONArray) value;
                        ArrayList<String> list = new ArrayList<>();

                        for (int i = 0; i < jsonArray.length(); i++) {
                            list.add(jsonArray.getString(i));
                        }

                        field.set(instance, list);
                    }
                    else {
                        field.set(instance, value);
                    }
                }
            }

            return instance;

        } catch (IOException | IllegalAccessException | JSONException e) {
            throw new RuntimeException("Error loading from file", e);
        }
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void addUnsolved() {
        wordBuffer.add(currentWord);
    }

    public String getSolvedWords() {
        return String.valueOf(solvedWords);
    }

    public String getTotalWords() {
        return String.valueOf(totalWords);
    }

    public void setFlag() {
        this.flag = true;
    }
}
