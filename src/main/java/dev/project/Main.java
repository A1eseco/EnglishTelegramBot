package dev.project;

import dev.project.client.Client;
import dev.project.telegram.EnglishBot;
import dev.project.translating.WordRandomizer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main {
    private static EnglishBot englishBot;
    private static JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
        new Thread(()->{startBot();}).start();
    }

    private static void startBot() {
        try {
            new WordRandomizer();
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            englishBot = new EnglishBot();
            api.registerBot(englishBot);
            System.out.println("Бот запущен");
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createAndShowGUI() {
        frame = new JFrame("Управление ботом");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 150);
        frame.setLocationRelativeTo(null);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JLabel statusLabel = new JLabel("Статус: Бот запущен", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(statusLabel, BorderLayout.CENTER);
        JButton stopButton = new JButton("Остановить бота");
        stopButton.setFont(new Font("Arial", Font.PLAIN, 14));
        stopButton.setBackground(new Color(220, 80, 80));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (englishBot != null) {
                    englishBot.stopBot();
                    englishBot.onClosing();
                    stopButton.setEnabled(false);
                    frame.dispose();
                    System.out.println("Программа завершена.");
                    System.exit(0);
                }
            }
        });
        panel.add(stopButton, BorderLayout.SOUTH);
        frame.add(panel);
        frame.setVisible(true);
    }
}