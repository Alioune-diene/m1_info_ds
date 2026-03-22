package fr.uga.im2ag.m1info.physical;

@FunctionalInterface
public interface MessageHandler {
    void onMessage(Message message);
}