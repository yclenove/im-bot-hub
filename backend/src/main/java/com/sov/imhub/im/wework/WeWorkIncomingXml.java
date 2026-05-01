package com.sov.imhub.im.wework;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/** 解密后的企业微信消息 XML（仅解析本 bot 需要的字段）。 */
public record WeWorkIncomingXml(
        String msgType, String content, String fromUserName, String toUserName, String agentId) {

    public static WeWorkIncomingXml parse(String decryptedXml) {
        if (decryptedXml == null || decryptedXml.isBlank()) {
            return new WeWorkIncomingXml("", "", "", "", "");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setExpandEntityReferences(false);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new InputSource(new StringReader(decryptedXml.trim())));
            Element root = doc.getDocumentElement();
            String agent = text(root, "AgentID");
            if (agent.isEmpty()) {
                agent = text(root, "AgentId");
            }
            return new WeWorkIncomingXml(
                    text(root, "MsgType"),
                    text(root, "Content"),
                    text(root, "FromUserName"),
                    text(root, "ToUserName"),
                    agent);
        } catch (Exception e) {
            return new WeWorkIncomingXml("", "", "", "", "");
        }
    }

    private static String text(Element root, String tag) {
        var nl = root.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return "";
        }
        String t = nl.item(0).getTextContent();
        return t == null ? "" : t.trim();
    }
}
