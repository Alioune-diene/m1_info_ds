package fr.uga.im2ag.m1info.tchatsapp.client.handlers;

import fr.uga.im2ag.m1info.tchatsapp.client.command.PendingCommandManager;
import fr.uga.im2ag.m1info.tchatsapp.client.media.MediaManager;

/**
 * Context providing dependencies for client packet handlers initialization.
 */
public class ClientHandlerContext {
    private final PendingCommandManager commandManager;
    private final MediaManager mediaManager;

    private ClientHandlerContext(Builder builder) {
        this.commandManager = builder.commandManager;
        this.mediaManager = builder.mediaManager;
    }

    public PendingCommandManager getCommandManager() {
        return commandManager;
    }

    public MediaManager getMediaManager() {
        return mediaManager;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PendingCommandManager commandManager;
        private MediaManager mediaManager;

        public Builder commandManager(PendingCommandManager commandManager) {
            this.commandManager = commandManager;
            return this;
        }

        public Builder mediaManager(MediaManager mediaManager) {
            this.mediaManager = mediaManager;
            return this;
        }

        public ClientHandlerContext build() {
            return new ClientHandlerContext(this);
        }
    }
}