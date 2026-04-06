package fr.uga.im2ag.m1info.physical;
//  A simple callback interface used to handle incoming messages
// Any class that wants to react to received messages implements this interface
@FunctionalInterface
public interface MessageHandler {
    void onMessage(Message message);
}