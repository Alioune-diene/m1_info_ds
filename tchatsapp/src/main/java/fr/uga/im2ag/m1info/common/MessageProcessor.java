package fr.uga.im2ag.m1info.common;

import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;

public interface MessageProcessor {
    void process(ProtocolMessage message);
}
