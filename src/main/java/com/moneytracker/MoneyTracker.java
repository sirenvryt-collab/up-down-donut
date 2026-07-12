package com.moneytracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the tracked balance / marked point and does the regex parsing.
 * Everything here is client-only and never talks to a server.
 */
public class MoneyTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("moneytracker.json");

    // Default pattern matches things like "$1,234.56", "$ 1000", "-$50.00"
    private static final String DEFAULT_PATTERN = "-?\\$\\s?(-?[0-9][0-9,]*(?:\\.[0-9]+)?)";

    public static volatile Double currentBalance = null;
    public static volatile Double markedBalance = null;
    public static volatile long markedTimeMillis = 0L;
    public static volatile boolean hudEnabled = true;
    public static volatile String patternString = DEFAULT_PATTERN;

    public static volatile boolean autoEnabled = false;
    public static volatile int autoIntervalSeconds = 30;
    public static volatile String autoCommand = ",bal";

    private static Pattern pattern = Pattern.compile(DEFAULT_PATTERN);

    private MoneyTracker() {
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                SaveData data = GSON.fromJson(json, SaveData.class);
                if (data != null) {
                    currentBalance = data.currentBalance;
                    markedBalance = data.markedBalance;
                    markedTimeMillis = data.markedTimeMillis;
                    hudEnabled = data.hudEnabled;
                    if (data.patternString != null && !data.patternString.isEmpty()) {
                        setPattern(data.patternString);
                    }
                    autoEnabled = data.autoEnabled;
                    if (data.autoIntervalSeconds > 0) {
                        autoIntervalSeconds = data.autoIntervalSeconds;
                    }
                    if (data.autoCommand != null && !data.autoCommand.isEmpty()) {
                        autoCommand = data.autoCommand;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void save() {
        try {
            SaveData data = new SaveData();
            data.currentBalance = currentBalance;
            data.markedBalance = markedBalance;
            data.markedTimeMillis = markedTimeMillis;
            data.hudEnabled = hudEnabled;
            data.patternString = patternString;
            data.autoEnabled = autoEnabled;
            data.autoIntervalSeconds = autoIntervalSeconds;
            data.autoCommand = autoCommand;
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Tries to compile a new regex. The regex must contain exactly one
     * capture group which wraps the numeric amount (commas allowed).
     */
    public static boolean setPattern(String regex) {
        try {
            Pattern p = Pattern.compile(regex);
            pattern = p;
            patternString = regex;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called for every chat / game / action bar message the client receives.
     * If the message contains something matching the money pattern, we
     * update the currently known balance.
     */
    public static void onMessage(String text) {
        if (text == null || text.isEmpty()) return;
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                String raw = m.group(1).replace(",", "");
                double value = Double.parseDouble(raw);
                currentBalance = value;
                save();
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                // Pattern matched but had no usable numeric group - ignore.
            }
        }
    }

    public static void mark() {
        markedBalance = currentBalance;
        markedTimeMillis = System.currentTimeMillis();
        save();
    }

    public static void reset() {
        markedBalance = null;
        markedTimeMillis = 0L;
        save();
    }

    public static Double getDiff() {
        if (currentBalance == null || markedBalance == null) return null;
        return currentBalance - markedBalance;
    }

    private static class SaveData {
        Double currentBalance;
        Double markedBalance;
        long markedTimeMillis;
        boolean hudEnabled = true;
        String patternString;
        boolean autoEnabled = false;
        int autoIntervalSeconds = 30;
        String autoCommand = ",bal";
    }
}
