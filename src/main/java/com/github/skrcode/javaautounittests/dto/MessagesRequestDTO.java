/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.dto;

import java.util.ArrayList;
import java.util.List;

public final class MessagesRequestDTO {

    private final List<Message> messages;
    private final List<Message> actualMessages;

    public MessagesRequestDTO() {
        this.messages = new ArrayList<>();
        this.actualMessages = new ArrayList<>();
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<Message> getActualMessages() {
        return actualMessages;
    }

    public MessagesRequestDTO addMessage(Message message) {
        if(message != null) this.messages.add(message);
        return this;
    }

    public MessagesRequestDTO addAllMessages(List<Message> message) {
        this.messages.addAll(message);
        return this;
    }

    public MessagesRequestDTO addActualMessage(Message message) {
        if(message != null) this.actualMessages.add(message);
        return this;
    }

    public MessagesRequestDTO setActualMessages(List<Message> messages) {
        this.actualMessages.clear();
        if (messages != null) {
            this.actualMessages.addAll(messages);
        }
        return this;
    }

    public MessagesRequestDTO addToBoth(Message message) {
        this.messages.add(message);
        this.actualMessages.add(message);
        return this;
    }
}
