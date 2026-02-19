package fr.uga.im2ag.m1info.tchatsapp.common;

import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

public interface MessageProcessor {
    void process(ProtocolMessage message);
}
