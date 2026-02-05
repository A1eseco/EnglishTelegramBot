package dev.project.translating;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SimpleGoogleTranslator {
    private OkHttpClient client = new OkHttpClient();
    
    public String translate(String text, String sourceLang, String targetLang) {
        String encodedText = null;
        try {
            encodedText = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        String url = "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + sourceLang + "&" +
                "tl=" + targetLang + "&" +
                "dt=t&" +
                "q=" + encodedText;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Ошибка HTTP: " + response.code());
            }
            
            String responseBody = response.body().string();
            return parseGoogleResponse(responseBody);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private String parseGoogleResponse(String response) {
        try {
            JSONArray jsonArray = new JSONArray(response);
            JSONArray translations = jsonArray.getJSONArray(0);

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < translations.length(); i++) {
                JSONArray translation = translations.getJSONArray(i);
                result.append(translation.getString(0));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга: " + response.substring(0, Math.min(100, response.length())), e);
        }
    }
    void main() {
        System.out.println(translate("upon", "en", "ru"));
    }
}