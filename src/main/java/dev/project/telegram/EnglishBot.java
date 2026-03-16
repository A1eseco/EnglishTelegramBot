package dev.project.telegram;

import dev.project.client.Client;
import dev.project.translating.TranslationValidator;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class EnglishBot extends TelegramLongPollingBot {
    private HashMap<Long, Client> clients = new HashMap<>();
    private HashMap<String, List<Client>> clientsTimes = new HashMap<>();
    private HashMap<String, Consumer<Client>> commands = new HashMap<>();
    private HashMap<String, byte[]> fileBytesCache = new HashMap<>();
    private TranslationValidator validator = new TranslationValidator();
    private String directory = "clients" + File.separator;
    private boolean isRunning = true;
    private Thread timeThread;

    public EnglishBot() {
        createCommands();
        loadClients();
        startTimeThread();
    }

    private void loadClients() {
        File clientDir = new File(directory);
        if (!clientDir.exists()) {
            clientDir.mkdirs();
        }
        File[] files = clientDir.listFiles();
        if (files != null) {
            for (File file : files) {
                Client client = Client.load(file);
                clients.put(client.getChatIDasLong(), client);
                addClientToTimeMap(client.getTime(), client);
            }
        }
    }

    private void startTimeThread() {
        timeThread = new Thread(() -> {
            while (isRunning) {
                ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                String now = zonedDateTime.format(formatter);
                System.out.println(now);
                if (clientsTimes.containsKey(now)) {
                    System.out.println("contains");
                    List<Client> toNotify = new ArrayList<>(clientsTimes.get(now));
                    for (Client client : toNotify) {
                        CompletableFuture.runAsync(() -> {
                            if (!client.isDictation()) {
                                try {
                                    SendMessage message = sendMessage(client.getChatID(), "✍️ *Время для диктанта\\!* Выберите режим\\:");
                                    message.setReplyMarkup(createDictationModeMarkup());
                                    execute(message);
                                }
                                catch (TelegramApiException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }
                try {
                    Thread.sleep(31000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        timeThread.start();
    }

    private void addClientToTimeMap(String time, Client client) {
        if (time != null && !time.isEmpty()) {
            clientsTimes.computeIfAbsent(time, k -> new ArrayList<>()).add(client);
        }
    }

    private void removeClientFromTimeMap(String time, Client client) {
        if (clientsTimes.containsKey(time)) {
            clientsTimes.get(time).remove(client);
            if (clientsTimes.get(time).isEmpty()) {
                clientsTimes.remove(time);
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "glossary_english_bot";
    }

    @Override
    public String getBotToken() {
        return "8265544625:AAFhyLfYtBMiWOMivnLiET3F9aF9mWueVqA";
    }

    @Override
    public void onUpdateReceived(Update update) {
        CompletableFuture.runAsync(() -> {
            try {
                handleCallBacks(update);
                handleMessages(update);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleCallBacks(Update update) throws TelegramApiException {
        if (update.hasCallbackQuery()) {
            CallbackQuery query = update.getCallbackQuery();
            String chatID = query.getMessage().getChatId().toString();
            long chatIDLong = query.getMessage().getChatId();
            Integer messageId = query.getMessage().getMessageId();
            String data = query.getData();

            AnswerCallbackQuery answer = new AnswerCallbackQuery(query.getId());
            execute(answer);

            if (data.startsWith("dictation_mode:")) {
                String mode = data.substring("dictation_mode:".length());
                Client client = clients.computeIfAbsent(chatIDLong,
                        k -> new Client(chatIDLong, query.getFrom().getFirstName()));

                DeleteMessage deleteMessage = new DeleteMessage(chatID, messageId);
                execute(deleteMessage);

                if (!client.isDictation()) {
                    client.startDictation(mode);
                    String modeLabel = "EN_TO_RU".equals(mode)
                            ? "🇬🇧 Английский → Русский"
                            : "🇷🇺 Русский → Английский";
                    String word = getWordForClient(client);
                    execute(sendMessage(chatID, "✍️ *Начало диктанта* \\(" + modeLabel + "\\)\\.\nПервое слово: *" + escapeMarkdown(word) + "*"));
                }
            } else {
                DeleteMessage deleteMessage = new DeleteMessage(chatID, messageId);
                execute(deleteMessage);

                SendPhoto form = sendPhoto(chatID, data + "_form.png");
                SendPhoto use = sendPhoto(chatID, data + "_use.png");
                execute(form);
                execute(use);
            }
        }
    }

    private void handleMessages(Update update) throws TelegramApiException {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatID = message.getChatId();
            String msgText = message.getText();
            if (msgText == null) return;

            Client client = clients.computeIfAbsent(chatID, k -> new Client(chatID, message.getFrom().getFirstName()));

            if (msgText.startsWith("/")) {
                handleCommands(msgText, client);
            } else if (client.isDictation()) {
                handleDictation(msgText, client);
            }
        }
    }

    private String getWordForClient(Client client) {
        String englishWord = client.getCurrentWord();
        if (client.isEnToRu()) {
            return englishWord;
        } else {
            return validator.getTranslator().translate(englishWord, "en", "ru");
        }
    }

    private void handleDictation(String userTranslation, Client client) throws TelegramApiException {
        SendMessage sendMessage;
        boolean correct;

        if (client.isEnToRu()) {
            correct = validator.validateTranslation(client.getCurrentWord(), userTranslation, "ru", "en");
            if (correct) {
                String straight = validator.getTranslator().translate(client.getCurrentWord(), "en", "ru");
                if (userTranslation.toLowerCase().equals(straight.toLowerCase())) {
                    sendMessage = sendMessage(client.getChatID(), "✅ *Правильно, молодец\\!* 🎉");
                } else {
                    sendMessage = sendMessage(client.getChatID(), "✅ *Правильно, молодец\\!* 🎉\nПрямой перевод слова\\: *" + escapeMarkdown(straight) + "*");
                }
                client.addSolvedWord();
            } else {
                String hint = validator.getTranslator().translate(client.getCurrentWord(), "en", "ru");
                sendMessage = sendMessage(client.getChatID(), "❌ *Неправильно*\\.\nОдин из вариантов перевода: *" + escapeMarkdown(hint) + "*");
                client.addUnsolved();
                client.addWord();
            }
        } else {
            String cleanUser = userTranslation.toLowerCase().trim();
            String cleanTarget = client.getCurrentWord().toLowerCase().trim();
            correct = cleanUser.equals(cleanTarget)
                    || validator.validateTranslation(userTranslation, client.getCurrentWord(), "en", "en");
            if (!correct) {
                String userToRu = validator.getTranslator().translate(cleanUser, "en", "ru");
                String targetToRu = validator.getTranslator().translate(cleanTarget, "en", "ru");
                correct = userToRu.equalsIgnoreCase(targetToRu);
            }
            if (correct) {
                if (cleanUser.equals(cleanTarget)) {
                    sendMessage = sendMessage(client.getChatID(), "✅ *Правильно, молодец\\!* 🎉");
                } else {
                    sendMessage = sendMessage(client.getChatID(), "✅ *Правильно, молодец\\!* 🎉\nТочный ответ\\: *" + escapeMarkdown(client.getCurrentWord()) + "*");
                }
                client.addSolvedWord();
            } else {
                sendMessage = sendMessage(client.getChatID(), "❌ *Неправильно*\\.\nПравильный ответ: *" + escapeMarkdown(client.getCurrentWord()) + "*");
                client.addUnsolved();
                client.addWord();
            }
        }

        execute(sendMessage);

        if (client.isDictationEnds()) {
            SendMessage send = sendMessage(client.getChatID(),
                    ("🏁 *Диктант окончен*. \nТвой результат: *" + client.getSolvedPercentage() + "\\%* \uD83C\uDFAF").replace(".", "\\."));
            execute(send);
            client.endDictation(directory);
            return;
        }

        client.processDictation();
        String nextWord = getWordForClient(client);
        SendMessage send = sendMessage(client.getChatID(), "➡️ *Следующее слово*\\: *" + escapeMarkdown(nextWord) + "*");
        execute(send);
    }

    private void handleCommands(String msgText, Client client) throws TelegramApiException {
        if (commands.containsKey(msgText)) {
            commands.get(msgText).accept(client);
        } else if (msgText.startsWith("/time")) {
            handleTimeCommand(msgText, client);
        } else if (msgText.startsWith("/te")) {
            String[] s = msgText.split(" ", 2);
            if (s.length < 2) {
                execute(sendMessage(client.getChatID(), "❌ *Неверный формат команды*\\. Вы не написали текст\\, который необходимо перевести\\.\n*Пример:* `/te Hello`"));
            } else {
                String translated = validator.getTranslator().translate(s[1], "en", "ru").replace("!", "\\!");
                execute(sendMessage(client.getChatID(), "\uD83C\uDDF7\uD83C\uDDFA *Перевод*\\: " + translated));
            }
        } else if (msgText.startsWith("/tr")) {
            String[] s = msgText.split(" ", 2);
            if (s.length < 2) {
                execute(sendMessage(client.getChatID(), "❌ *Неверный формат команды*\\. Вы не написали текст\\, который необходимо перевести\\.\n*Пример:* `/tr Привет`"));
            } else {
                String translated = validator.getTranslator().translate(s[1], "ru", "en").replace("!", "\\!");
                execute(sendMessage(client.getChatID(), "\uD83C\uDDEC\uD83C\uDDE7 *Перевод*\\: " + translated));
            }
        }
    }

    private void handleTimeCommand(String msgText, Client client) throws TelegramApiException {
        SimpleDateFormat dateFmt = new SimpleDateFormat("HH:mm");
        String nowStr = dateFmt.format(new Date());

        String[] s = msgText.split(" ");
        if (s.length > 1 && isValidTime(s[1])) {
            String time = s[1];
            SendMessage message;
            if (client.getTime() != null && !client.getTime().isEmpty()) {
                message = sendMessage(client.getChatID(), "✏️ *Перезаписал*\\.\nВаше новое время: *" + time + "* ⏰");
                removeClientFromTimeMap(client.getTime(), client);
            } else {
                message = sendMessage(client.getChatID(), "✅ *Записал*\\.\nВаше время: *" + time + "* ⏰");
            }
            client.setTime(time);
            addClientToTimeMap(time, client);
            execute(message);
        } else {
            execute(sendMessage(client.getChatID(), "⏰ *Укажите время* в формате: `/time " + roundToNext30Minutes(LocalTime.parse(nowStr)) + "` \\(МСК\\)\\."));
        }
    }

    public static LocalTime roundToNext30Minutes(LocalTime time) {
        int minute = time.getMinute();
        if (minute == 0) return time.truncatedTo(ChronoUnit.HOURS);
        if (minute <= 30) return time.withMinute(30).truncatedTo(ChronoUnit.MINUTES);
        return time.plusHours(1).withMinute(0).truncatedTo(ChronoUnit.MINUTES);
    }

    private InlineKeyboardMarkup createDictationModeMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton btnEnRu = new InlineKeyboardButton();
        btnEnRu.setText("🇬🇧 En → Ru");
        btnEnRu.setCallbackData("dictation_mode:EN_TO_RU");

        InlineKeyboardButton btnRuEn = new InlineKeyboardButton();
        btnRuEn.setText("🇷🇺 Ru → En");
        btnRuEn.setCallbackData("dictation_mode:RU_TO_EN");

        row.add(btnEnRu);
        row.add(btnRuEn);
        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    private String escapeMarkdown(String text) {
        return text.replace("-", "\\-").replace(".", "\\.").replace("!", "\\!")
                   .replace("(", "\\(").replace(")", "\\)").replace("|", "\\|")
                   .replace("[", "\\[").replace("]", "\\]");
    }

    private InlineKeyboardMarkup createInlineMarkup() {
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton b1 = new InlineKeyboardButton();
        b1.setText("⏳ Present времена"); b1.setCallbackData("present");
        row1.add(b1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton b2 = new InlineKeyboardButton();
        b2.setText("⏳ Past времена"); b2.setCallbackData("past");
        InlineKeyboardButton b3 = new InlineKeyboardButton();
        b3.setText("⏳ Future времена"); b3.setCallbackData("future");
        row2.add(b2); row2.add(b3);

        rows.add(row1); rows.add(row2);
        inlineKeyboard.setKeyboard(rows);
        return inlineKeyboard;
    }

    public SendPhoto sendPhoto(String chatId, String name) {
        InputFile inputFile;
        try {
            if (fileBytesCache.containsKey(name)) {
                inputFile = new InputFile(new java.io.ByteArrayInputStream(fileBytesCache.get(name)), name);
            } else {
                String resourcePath = "image_files/" + name;
                InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

                if (is == null) {
                    System.err.println("❌ Ресурс " + resourcePath + " не найден!");
                    return new SendPhoto(chatId, new InputFile());
                }
                byte[] bytes = is.readAllBytes();
                is.close();
                fileBytesCache.put(name, bytes);

                inputFile = new InputFile(new java.io.ByteArrayInputStream(bytes), name);
            }

            return new SendPhoto(chatId, inputFile);

        } catch (java.io.IOException e) {
            System.err.println("❌ Ошибка при работе с файлом " + name + ": " + e.getMessage());
            return new SendPhoto(chatId, new InputFile());
        }
    }

    public SendMessage sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        message.setParseMode("MarkdownV2");
        return message;
    }

    public void createCommands() {
        String greeting = "👋 *Привет\\! Я Глосс \\- твой личный репетитор английского\\!*\n\n" +
                "🌟 *Вот чем я могу быть тебе полезен\\:*\n" +
                "・ 🧠 *Перевод*\\: Быстро и точно перевожу слова и целые предложения\\.\n" +
                "・ ✍️ *Тренировки*\\: Устраиваю проверку перевода слов, чтобы они точно запомнились\\.\n" +
                "・ ➡️ *Чтобы начать тренировки*\\, просто выбери время для них командой `/time HH\\:MM` \\(МСК\\)\\.\n" +
                "・ 📚 *Материалы*\\: Выдаю таблицы времен, списки неправильных глаголов и другие полезные шпаргалки `/refmaterials`\\.\n\n" +
                "💡 *Как говорил Эдвард де Боно*\\:\n" +
                "`«Чтобы говорить на иностранном языке\\, нужен словарный запас\\, а не грамматика\\. Чтобы свободно говорить на иностранном языке\\,\n" +
                "вам нужно 1000 слов\\. Чтобы читать газеты\\, вам нужно 3000\\, а чтобы читать великие произведения мировой литературы\\,\n" +
                "вам нужно 5000 слов»`\\.\n\n" +
                "Сфокусируемся на самом главном \\- твоём словарном запасе\\. Начнём\\? 😊";

        commands.put("/start", (client) -> {
            SendMessage msg = sendMessage(client.getChatID(), greeting);
            msg.setReplyMarkup(new ReplyKeyboardRemove(true));
            try { execute(msg); } catch (Exception e) { e.printStackTrace(); }
        });

        commands.put("/refmaterials", (client) -> {
            SendMessage message = sendMessage(client.getChatID(), "📚 *Выберите материалы*\\:");
            message.setReplyMarkup(createInlineMarkup());
            try { execute(message); } catch (Exception e) { e.printStackTrace(); }
        });

        commands.put("/stat", (client) -> {
            String stat = ("*Статистика 📊*\n" +
                    "📝 *Общее количество слов:* " + client.getTotalWords() + "." + "\n" +
                    "✅ *Количество правильных переводов:* " + client.getSolvedWords() + "." + "\n" +
                    "📈 *Процент правильных ответов:* " + client.getTotalSolvedPercentage() + "%").replace(".", "\\.");
            try { execute(sendMessage(client.getChatID(), stat)); } catch (Exception e) { e.printStackTrace(); }
        });
        commands.put("/dictation", (client) -> {
            if (!client.isDictation()) {
                SendMessage message = sendMessage(client.getChatID(), "✍️ *Выберите режим диктанта*\\:");
                message.setReplyMarkup(createDictationModeMarkup());
                try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
            } else {
                try {
                    execute(sendMessage(client.getChatID(), "⏳ *Диктант уже идет\\!* Текущее слово: *" + escapeMarkdown(client.getCurrentWord()) + "*"));
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        });
        commands.put("/stop", (client) -> {
            if (client.isDictation()) {
                client.endDictation(directory);
                client.setFlag();
                try {
                    execute(sendMessage(client.getChatID(), "⏸ *Диктант остановлен\\.* \nМы продолжим в следующий раз\\."));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    execute(sendMessage(client.getChatID(), "❌ Вы сейчас не проходите диктант\\."));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean isValidTime(String time) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
        format.setLenient(false);
        try { format.parse(time); return true; } catch (ParseException e) { return false; }
    }

    public void stopBot() {
        isRunning = false;
        if (timeThread != null) timeThread.interrupt();
        for (Client client : clients.values()) { client.save(directory); }
        clearWebhook();
    }

    @Override
    public void clearWebhook() {
        try { execute(new org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook()); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
}