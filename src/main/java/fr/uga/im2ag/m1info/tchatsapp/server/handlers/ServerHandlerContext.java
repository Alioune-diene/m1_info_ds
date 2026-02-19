package fr.uga.im2ag.m1info.tchatsapp.server.handlers;

/**
 * Context providing dependencies for server packet handlers initialization.
 * Currently empty but extensible for future needs.
 */
public class ServerHandlerContext {

    private ServerHandlerContext(Builder builder) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public ServerHandlerContext build() {
            return new ServerHandlerContext(this);
        }
    }
}