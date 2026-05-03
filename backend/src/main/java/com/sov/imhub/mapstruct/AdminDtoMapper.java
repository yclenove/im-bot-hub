package com.sov.imhub.mapstruct;

import com.sov.imhub.admin.dto.BotResponse;
import com.sov.imhub.admin.dto.DatasourceResponse;
import com.sov.imhub.admin.dto.FieldMappingResponse;
import com.sov.imhub.admin.dto.QueryDefinitionResponse;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.DatasourceEntity;
import com.sov.imhub.domain.FieldMappingEntity;
import com.sov.imhub.domain.QueryDefinitionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminDtoMapper {

    @Mapping(target = "enabled", expression = "java(Boolean.TRUE.equals(bot.getEnabled()))")
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
