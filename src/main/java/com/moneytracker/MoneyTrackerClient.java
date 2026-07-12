package com.moneytracker;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MoneyTrackerClient implements ClientModInitializer {

    private static int autoTickCounter = 0;

    @Override
    public void onInitializeClient() {
        MoneyTracker.load();

        // Watch every message the client receives (server system messages,
        // action bar text, and player chat) and try to pull a $ amount out of it.
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                MoneyTracker.onMessage(message.getString()));

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                MoneyTracker.onMessage(message.getString()));

        // Periodically re-send the balance command on its own, if auto-check is enabled.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!MoneyTracker.autoEnabled) return;
            if (client.player == null || client.getConnection() == null) return;

            int intervalTicks = Math.max(MoneyTracker.autoIntervalSeconds, 5) * 20;
            autoTickCounter++;
            if (autoTickCounter >= intervalTicks) {
                autoTickCounter = 0;
                client.player.connection.sendChat(MoneyTracker.autoCommand);
            }
        });

        registerCommands();
        registerHud();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(ClientCommandManager.literal("moneymark")
                    .executes(MoneyTrackerClient::executeMark));

            dispatcher.register(ClientCommandManager.literal("moneycheck")
                    .executes(MoneyTrackerClient::executeCheck));

            dispatcher.register(ClientCommandManager.literal("moneyreset")
                    .executes(MoneyTrackerClient::executeReset));

            dispatcher.register(ClientCommandManager.literal("moneyhud")
                    .executes(MoneyTrackerClient::executeToggleHud));

            dispatcher.register(ClientCommandManager.literal("moneyset")
                    .then(ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg())
                            .executes(MoneyTrackerClient::executeSet)));

            dispatcher.register(ClientCommandManager.literal("moneypattern")
                    .then(ClientCommandManager.argument("regex", StringArgumentType.greedyString())
                            .executes(MoneyTrackerClient::executePattern)));

            dispatcher.register(ClientCommandManager.literal("moneyauto")
                    .executes(MoneyTrackerClient::executeAutoStatus)
                    .then(ClientCommandManager.literal("off")
                            .executes(MoneyTrackerClient::executeAutoOff))
                    .then(ClientCommandManager.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5))
                            .executes(MoneyTrackerClient::executeAutoOn)));

            dispatcher.register(ClientCommandManager.literal("moneyautocmd")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(MoneyTrackerClient::executeAutoCommand)));
        });
    }

    private static int executeMark(CommandContext<FabricClientCommandSource> context) {
        MoneyTracker.mark();
        if (MoneyTracker.currentBalance == null) {
            context.getSource().sendFeedback(Component.literal(
                    "[MoneyTracker] Point marked, but I haven't read a balance yet. " +
                            "Run whatever command shows your balance (e.g. /bal) so I can pick it up, " +
                            "or set it manually with /moneyset <amount>."));
        } else {
            context.getSource().sendFeedback(Component.literal(
                    "[MoneyTracker] Point marked at $" + format(MoneyTracker.currentBalance)));
        }
        return 1;
    }

    private static int executeCheck(CommandContext<FabricClientCommandSource> context) {
        StringBuilder sb = new StringBuilder("[MoneyTracker] ");
        if (MoneyTracker.currentBalance == null) {
            sb.append("No balance has been read yet. Run your balance command " +
                    "or use /moneyset <amount>.");
        } else if (MoneyTracker.markedBalance == null) {
            sb.append("Current: $").append(format(MoneyTracker.currentBalance))
                    .append(" (no point marked - use /moneymark)");
        } else {
            double diff = MoneyTracker.currentBalance - MoneyTracker.markedBalance;
            String sign = diff >= 0 ? "+" : "-";
            sb.append("Current: $").append(format(MoneyTracker.currentBalance));
            sb.append(" | Marked: $").append(format(MoneyTracker.markedBalance));
            sb.append(" | Change: ").append(sign).append("$").append(format(Math.abs(diff)));
            sb.append(" | ").append(formatDuration(System.currentTimeMillis() - MoneyTracker.markedTimeMillis))
                    .append(" ago");
        }
        context.getSource().sendFeedback(Component.literal(sb.toString()));
        return 1;
    }

    private static int executeReset(CommandContext<FabricClientCommandSource> context) {
        MoneyTracker.reset();
        context.getSource().sendFeedback(Component.literal("[MoneyTracker] Marked point cleared."));
        return 1;
    }

    private static int executeToggleHud(CommandContext<FabricClientCommandSource> context) {
        MoneyTracker.hudEnabled = !MoneyTracker.hudEnabled;
        MoneyTracker.save();
        context.getSource().sendFeedback(Component.literal(
                "[MoneyTracker] HUD " + (MoneyTracker.hudEnabled ? "enabled" : "disabled") + "."));
        return 1;
    }

    private static int executeSet(CommandContext<FabricClientCommandSource> context) {
        double amount = DoubleArgumentType.getDouble(context, "amount");
        MoneyTracker.currentBalance = amount;
        MoneyTracker.save();
        context.getSource().sendFeedback(Component.literal(
                "[MoneyTracker] Balance manually set to $" + format(amount)));
        return 1;
    }

    private static int executePattern(CommandContext<FabricClientCommandSource> context) {
        String regex = StringArgumentType.getString(context, "regex");
        boolean ok = MoneyTracker.setPattern(regex);
        MoneyTracker.save();
        context.getSource().sendFeedback(Component.literal(ok
                ? "[MoneyTracker] Pattern updated. Remember it needs exactly one capture group " +
                "around the number, e.g. \\$(\\d+(?:\\.\\d+)?)"
                : "[MoneyTracker] That regex failed to compile - nothing changed."));
        return 1;
    }

    private static int executeAutoStatus(CommandContext<FabricClientCommandSource> context) {
        String status = MoneyTracker.autoEnabled
                ? "ON, every " + MoneyTracker.autoIntervalSeconds + "s, sending \"" + MoneyTracker.autoCommand + "\""
                : "OFF";
        context.getSource().sendFeedback(Component.literal(
                "[MoneyTracker] Auto-check is " + status +
                        ". Use /moneyauto <seconds> or /moneyauto off."));
        return 1;
    }

    private static int executeAutoOn(CommandContext<FabricClientCommandSource> context) {
        int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds");
        MoneyTracker.autoIntervalSeconds = seconds;
        MoneyTracker.autoEnabled = true;
        autoTickCounter = 0;
        MoneyTracker.save();
        context.getSource().sendFeedback(Component.literal(
                "[MoneyTracker] Auto-check enabled: sending \"" + MoneyTracker.autoCommand +
                        "\" every " + seconds + "s."));
        return 1;
    }

    private static int executeAutoOff(CommandContext<FabricClientCommandSource> context) {
        MoneyTracker.autoEnabled = false;
        MoneyTracker.save();
        context.getSource().sendFeedback(Component.literal("[MoneyTracker] Auto-check disabled."));
        return 1;
    }

    private static int executeAutoCommand(CommandContext<FabricClientCommandSource> context) {
        String text = StringArgumentType.getString(context, "text");
        MoneyTracker.autoCommand = text;
        MoneyTracker.save();
        context.getSource().sendFeedback(Component.literal(
                "[MoneyTracker] Auto-check will now send: \"" + text + "\""));
        return 1;
    }

    private void registerHud() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("moneytracker", "balance_hud"),
                MoneyTrackerClient::renderHud
        );
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!MoneyTracker.hudEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        int x = 6;
        int y = 6;

        String line1 = MoneyTracker.currentBalance == null
                ? "Balance: unknown (read a balance command first)"
                : "Balance: $" + format(MoneyTracker.currentBalance);
        graphics.drawString(mc.font, line1, x, y, 0xFFFFFF, true);

        if (MoneyTracker.markedBalance != null && MoneyTracker.currentBalance != null) {
            double diff = MoneyTracker.currentBalance - MoneyTracker.markedBalance;
            int color = diff >= 0 ? 0x55FF55 : 0xFF5555;
            String sign = diff >= 0 ? "+" : "-";
            String line2 = "Since mark: " + sign + "$" + format(Math.abs(diff));
            graphics.drawString(mc.font, line2, x, y + 10, color, true);
        }
    }

    private static String format(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) {
            return String.format("%,.2fT", value / 1_000_000_000_000.0);
        } else if (abs >= 1_000_000_000.0) {
            return String.format("%,.2fB", value / 1_000_000_000.0);
        } else if (abs >= 1_000_000.0) {
            return String.format("%,.2fM", value / 1_000_000.0);
        } else if (abs >= 1_000.0) {
            return String.format("%,.2fK", value / 1_000.0);
        }
        return String.format("%,.2f", value);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
