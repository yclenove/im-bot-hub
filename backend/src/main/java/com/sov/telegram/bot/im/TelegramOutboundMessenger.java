package com.sov.telegram.bot.im;

import com.sov.telegram.bot.service.telegram.TelegramApiClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TelegramOutboundMessenger implements OutboundMessenger {

    private final TelegramApiClient telegramApiClient;
    private final InboundCommandContext ctx;

    @Override
    public void sendRateLimited() {
        telegramApiClient.sendRateLimitedMessage(ctx.telegramBotToken(), ctx.telegramChatId());
    }

    @Override
    public void sendNotAllowed() {
        telegramApiClient.sendNotAllowedMessage(ctx.telegramBotToken(), ctx.telegramChatId());
    }

    @Override
    public void sendUnknownCommand(String command) {
        telegramApiClient.sendUnknownCommand(ctx.telegramBotToken(), ctx.telegramChatId(), command);
    }

    @Override
    public void sendMissingParam(String paramName) {
        telegramApiClient.sendMissingParamMessage(ctx.telegramBotToken(), ctx.telegramChatId(), paramName);
    }

    @Override
    public void sendParamUsageReminder(String usageTelegramHtml, String usagePlain) {
        telegramApiClient.sendMessage(ctx.telegramBotToken(), ctx.telegramChatId(), usageTelegramHtml);
    }

    @Override
    public void sendHelp(String helpTelegramHtml, String helpPlain) {
        telegramApiClient.sendMessage(ctx.telegramBotToken(), ctx.telegramChatId(), helpTelegramHtml);
    }

    @Override
    public void sendQueryResult(String bodyTelegramHtml, String bodyPlain) {
        telegramApiClient.sendMessage(ctx.telegramBotToken(), ctx.telegramChatId(), bodyTelegramHtml);
    }

    @Override
    public void sendQueryFailed() {
        telegramApiClient.sendQueryFailedMessage(ctx.telegramBotToken(), ctx.telegramChatId());
    }
}
