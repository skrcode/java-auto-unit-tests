/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.dto;

import java.util.ArrayList;
import java.util.List;

public final class MessagesContentsRequestDTO {

    private final List<Message.MessageContent> messageContentsModel;
    private final List<Message.MessageContent> actualMessageContentsModel;
    private final List<Message.MessageContent> messageContentsUser;
    private final List<Message.MessageContent> actualMessageContentsUser;

    public MessagesContentsRequestDTO() {
        this.messageContentsModel = new ArrayList<>();
        this.actualMessageContentsModel = new ArrayList<>();
        this.messageContentsUser = new ArrayList<>();
        this.actualMessageContentsUser = new ArrayList<>();
    }

    public MessagesContentsRequestDTO addMessageContentModel(Message.MessageContent messageContent) {
        this.messageContentsModel.add(messageContent);
        return this;
    }


    public MessagesContentsRequestDTO addActualMessageContentModel(Message.MessageContent messageContent) {
        this.actualMessageContentsModel.add(messageContent);
        return this;
    }

    public MessagesContentsRequestDTO addMessageContentUser(Message.MessageContent messageContent) {
        this.messageContentsUser.add(messageContent);
        return this;
    }


    public MessagesContentsRequestDTO addActualMessageContentUser(Message.MessageContent messageContent) {
        this.actualMessageContentsUser.add(messageContent);
        return this;
    }


    public MessagesContentsRequestDTO addToBothModel(Message.MessageContent messageContent) {
        this.messageContentsModel.add(messageContent);
        this.actualMessageContentsModel.add(messageContent);
        return this;
    }

    public MessagesContentsRequestDTO addToBothUser(Message.MessageContent messageContent) {
        this.messageContentsUser.add(messageContent);
        this.actualMessageContentsUser.add(messageContent);
        return this;
    }

    public List<Message.MessageContent> getMessageContentsModel() {
        return messageContentsModel;
    }

    public List<Message.MessageContent> getActualMessageContentsModel() {
        return actualMessageContentsModel;
    }

    public List<Message.MessageContent> getMessageContentsUser() {
        return messageContentsUser;
    }

    public List<Message.MessageContent> getActualMessageContentsUser() {
        return actualMessageContentsUser;
    }
}