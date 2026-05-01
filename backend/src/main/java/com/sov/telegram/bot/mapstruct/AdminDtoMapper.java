package com.sov.telegram.bot.mapstruct;

import com.sov.telegram.bot.admin.dto.BotResponse;
import com.sov.telegram.bot.admin.dto.DatasourceResponse;
import com.sov.telegram.bot.admin.dto.FieldMappingResponse;
import com.sov.telegram.bot.admin.dto.QueryDefinitionResponse;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.domain.FieldMappingEntity;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = SensitiveMasker.class)
public interface AdminDtoMapper {

    @Mapping(target = "telegramBotTokenMasked", source = "telegramBotToken", qualifiedByName = "maskToken")
    @Mapping(target = "webhookSecretTokenMasked", source = "webhookSecretToken", qualifiedByName = "maskOptionalToken")
    @Mapping(target = "enabled", expression = "java(Boolean.TRUE.equals(bot.getEnabled()))")
    @Mapping(
            target = "telegramChatScope",
            expression =
                    "java(bot.getTelegramChatScope() != null && !bot.getTelegramChatScope().isBlank() ? bot.getTelegramChatScope() : \"ALL\")")
    @Mapping(
            target = "telegramAllowedChatIds",
            expression = "java(com.sov.telegram.bot.util.TelegramChatIdsJson.parse(bot.getTelegramAllowedChatIdsJson()))")
    BotResponse toBotResponse(Bot bot);

    DatasourceResponse toDatasourceResponse(DatasourceEntity e);

    @Mapping(
            target = "queryMode",
            expression = "java(e.getQueryMode() != null && !e.getQueryMode().isBlank() ? e.getQueryMode() : \"SQL\")")
    @Mapping(
            target = "telegramReplyStyle",
            expression =
                    "java(e.getTelegramReplyStyle() != null && !e.getTelegramReplyStyle().isBlank() ? e.getTelegramReplyStyle() : \"LIST\")")
    QueryDefinitionResponse toQueryResponse(QueryDefinitionEntity e);

    FieldMappingResponse toFieldMappingResponse(FieldMappingEntity e);
}
