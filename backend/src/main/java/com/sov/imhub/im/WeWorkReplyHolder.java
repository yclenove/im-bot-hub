package com.sov.imhub.im;

import lombok.Getter;

/** 企业微信被动回复：明文 {@code success} 或 {@link me.chanjar.weixin.common.util.crypto.WxCryptUtil#encrypt} 生成的整段 XML。 */
@Getter
public final class WeWorkReplyHolder {

    private String body = "success";
    private boolean encrypted;

    public void setSuccessOnly() {
        this.body = "success";
        this.encrypted = false;
    }

    public void setEncryptedXml(String xml) {
        this.body = xml != null ? xml : "success";
        this.encrypted = xml != null && !xml.isBlank();
    }
}
