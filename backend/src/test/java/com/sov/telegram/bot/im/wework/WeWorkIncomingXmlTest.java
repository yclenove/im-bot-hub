package com.sov.telegram.bot.im.wework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeWorkIncomingXmlTest {

    @Test
    void parse_textMessage() {
        String xml =
                "<xml><ToUserName><![CDATA[corp]]></ToUserName>"
                        + "<FromUserName><![CDATA[user1]]></FromUserName>"
                        + "<AgentID>1000002</AgentID>"
                        + "<MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA[/cx 1]]></Content></xml>";
        WeWorkIncomingXml in = WeWorkIncomingXml.parse(xml);
        assertEquals("text", in.msgType());
        assertEquals("/cx 1", in.content());
        assertEquals("user1", in.fromUserName());
        assertEquals("corp", in.toUserName());
        assertEquals("1000002", in.agentId());
    }
}
