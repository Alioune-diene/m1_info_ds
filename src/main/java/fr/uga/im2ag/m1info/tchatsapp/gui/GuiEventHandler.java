package fr.uga.im2ag.m1info.tchatsapp.gui;

import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.system.EventSubscription;
import fr.uga.im2ag.m1info.tchatsapp.client.event.system.ExecutionMode;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles event subscriptions for the GUI layer.
 * All callbacks are dispatched to the Swing EDT via SwingUtilities.invokeLater().
 */
public class GuiEventHandler {
    private final ClientController controller;
    private final List<EventSubscription<?>> subscriptions;

    // Callbacks for UI updates
    private Consumer<ConnectionEstablishedEvent> onConnectionEstablished;
    private Consumer<TextMessageReceivedEvent> onTextMessageReceived;
    private Consumer<MediaMessageReceivedEvent> onMediaMessageReceived;
    private Consumer<ReactionMessageReceivedEvent> onReactionMessageReceived;
    private Consumer<ErrorEvent> onError;
    private Consumer<ContactAddedEvent> onContactAdded;
    private Consumer<ContactRemovedEvent> onContactRemoved;
    private Consumer<ContactUpdatedEvent> onContactUpdated;
    private Consumer<UserPseudoUpdatedEvent> onUserPseudoUpdated;
    private Consumer<MessageStatusChangedEvent> onMessageStatusChanged;
    private Consumer<ContactRequestReceivedEvent> onContactRequestReceived;
    private Consumer<ContactRequestResponseEvent> onContactRequestResponse;
    private Consumer<GroupCreateEvent> onGroupCreated;
    private Consumer<ChangeMemberInGroupEvent> onGroupMemberChanged;
    private Consumer<ManagementOperationSucceededEvent> onManagementOperationSucceeded;
    private Consumer<ManagementOperationFailedEvent> onManagementOperationFailed;
    private Consumer<UpdateGroupNameEvent> onUpdateGroupName;
    private Consumer<FileTransferProgressEvent> onFileTransferProgress;


    public GuiEventHandler(ClientController controller) {
        this.controller = controller;
        this.subscriptions = new ArrayList<>();
    }

    /**
     * Register all event subscriptions.
     * Must be called after setting up callbacks.
     */
    public void registerSubscriptions() {
        // Connection events
        subscriptions.add(controller.subscribeToEvent(
                ConnectionEstablishedEvent.class,
                this::handleConnectionEstablished,
                ExecutionMode.ASYNC
        ));

        // Message events
        subscriptions.add(controller.subscribeToEvent(
                TextMessageReceivedEvent.class,
                this::handleTextMessageReceived,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                MediaMessageReceivedEvent.class,
                this::handleMediaMessageReceived,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                MessageStatusChangedEvent.class,
                this::handleMessageStatusChanged,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ReactionMessageReceivedEvent.class,
                this::handleReactionMessageReceived,
                ExecutionMode.ASYNC
        ));

        // Contact events
        subscriptions.add(controller.subscribeToEvent(
                ContactAddedEvent.class,
                this::handleContactAdded,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ContactRemovedEvent.class,
                this::handleContactRemoved,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ContactUpdatedEvent.class,
                this::handleContactUpdated,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ContactRequestReceivedEvent.class,
                this::handleContactRequestReceived,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ContactRequestResponseEvent.class,
                this::handleContactRequestResponse,
                ExecutionMode.ASYNC
        ));

        // User events
        subscriptions.add(controller.subscribeToEvent(
                UserPseudoUpdatedEvent.class,
                this::handleUserPseudoUpdated,
                ExecutionMode.ASYNC
        ));

        // Group events
        subscriptions.add(controller.subscribeToEvent(
                GroupCreateEvent.class,
                this::handleGroupCreated,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ChangeMemberInGroupEvent.class,
                this::handleGroupMemberChanged,
                ExecutionMode.ASYNC
        ));

        // Error events
        subscriptions.add(controller.subscribeToEvent(
                ErrorEvent.class,
                this::handleError,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ManagementOperationSucceededEvent.class,
                this::handleManagementOperationSucceeded,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                ManagementOperationFailedEvent.class,
                this::handleManagementOperationFailed,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                UpdateGroupNameEvent.class,
                this::handleUpdateGroupName,
                ExecutionMode.ASYNC
        ));

        subscriptions.add(controller.subscribeToEvent(
                FileTransferProgressEvent.class,
                this::handleFileTransferProgress,
                ExecutionMode.SYNC
        ));
    }

    /**
     * Unsubscribe from all events.
     * Should be called when closing the application.
     */
    public void unsubscribeAll() {
        for (EventSubscription<?> subscription : subscriptions) {
            controller.unsubscribe(subscription);
        }
        subscriptions.clear();
    }

    // ----------------------- Event Handlers -----------------------

    private void handleConnectionEstablished(ConnectionEstablishedEvent event) {
        dispatchToEDT(onConnectionEstablished, event);
    }

    private void handleTextMessageReceived(TextMessageReceivedEvent event) {
        dispatchToEDT(onTextMessageReceived, event);
    }

    private void handleMediaMessageReceived(MediaMessageReceivedEvent event) {
        dispatchToEDT(onMediaMessageReceived, event);
    }

    private void handleReactionMessageReceived(ReactionMessageReceivedEvent event) {
        dispatchToEDT(onReactionMessageReceived, event);
    }

    private void handleMessageStatusChanged(MessageStatusChangedEvent event) {
        dispatchToEDT(onMessageStatusChanged, event);
    }

    private void handleContactAdded(ContactAddedEvent event) {
        dispatchToEDT(onContactAdded, event);
    }

    private void handleContactRemoved(ContactRemovedEvent event) {
        dispatchToEDT(onContactRemoved, event);
    }

    private void handleContactUpdated(ContactUpdatedEvent event) {
        dispatchToEDT(onContactUpdated, event);
    }

    private void handleContactRequestReceived(ContactRequestReceivedEvent event) {
        dispatchToEDT(onContactRequestReceived, event);
    }

    private void handleContactRequestResponse(ContactRequestResponseEvent event) {
        dispatchToEDT(onContactRequestResponse, event);
    }

    private void handleUserPseudoUpdated(UserPseudoUpdatedEvent event) {
        dispatchToEDT(onUserPseudoUpdated, event);
    }

    private void handleGroupCreated(GroupCreateEvent event) {
        dispatchToEDT(onGroupCreated, event);
    }

    private void handleGroupMemberChanged(ChangeMemberInGroupEvent event) {
        dispatchToEDT(onGroupMemberChanged, event);
    }

    private void handleError(ErrorEvent event) {
        dispatchToEDT(onError, event);
    }

    private void handleManagementOperationSucceeded (ManagementOperationSucceededEvent event) {
        dispatchToEDT(onManagementOperationSucceeded, event);
    }

    private void handleManagementOperationFailed (ManagementOperationFailedEvent event) {
        dispatchToEDT(onManagementOperationFailed, event);
    }

    private void handleUpdateGroupName (UpdateGroupNameEvent event) {
        dispatchToEDT(onUpdateGroupName, event);
    }

    private void handleFileTransferProgress(FileTransferProgressEvent event) {
        if (onFileTransferProgress != null) {
            onFileTransferProgress.accept(event);
        }
    }

    // ----------------------- Utility -----------------------

    private <T> void dispatchToEDT(Consumer<T> callback, T event) {
        if (callback != null) {
            SwingUtilities.invokeLater(() -> callback.accept(event));
        }
    }

    // ----------------------- Callback Setters -----------------------

    public void setOnConnectionEstablished(Consumer<ConnectionEstablishedEvent> callback) {
        this.onConnectionEstablished = callback;
    }

    public void setOnTextMessageReceived(Consumer<TextMessageReceivedEvent> callback) {
        this.onTextMessageReceived = callback;
    }

    public void setOnMediaMessageReceived(Consumer<MediaMessageReceivedEvent> callback) {
        this.onMediaMessageReceived = callback;
    }

    public void setOnReactionMessageReceived(Consumer<ReactionMessageReceivedEvent> callback) {
        this.onReactionMessageReceived = callback;
    }

    public void setOnError(Consumer<ErrorEvent> callback) {
        this.onError = callback;
    }

    public void setOnContactAdded(Consumer<ContactAddedEvent> callback) {
        this.onContactAdded = callback;
    }

    public void setOnContactRemoved(Consumer<ContactRemovedEvent> callback) {
        this.onContactRemoved = callback;
    }

    public void setOnContactUpdated(Consumer<ContactUpdatedEvent> callback) {
        this.onContactUpdated = callback;
    }

    public void setOnUserPseudoUpdated(Consumer<UserPseudoUpdatedEvent> callback) {
        this.onUserPseudoUpdated = callback;
    }

    public void setOnMessageStatusChanged(Consumer<MessageStatusChangedEvent> callback) {
        this.onMessageStatusChanged = callback;
    }

    public void setOnContactRequestReceived(Consumer<ContactRequestReceivedEvent> callback) {
        this.onContactRequestReceived = callback;
    }

    public void setOnContactRequestResponse(Consumer<ContactRequestResponseEvent> callback) {
        this.onContactRequestResponse = callback;
    }

    public void setOnGroupCreated(Consumer<GroupCreateEvent> callback) {
        this.onGroupCreated = callback;
    }

    public void setOnGroupMemberChanged(Consumer<ChangeMemberInGroupEvent> callback) {
        this.onGroupMemberChanged = callback;
    }

    public void setOnManagementOperationSucceeded(Consumer<ManagementOperationSucceededEvent> callback) {
        this.onManagementOperationSucceeded = callback;
    }

    public void setOnManagementOperationFailed(Consumer<ManagementOperationFailedEvent> callback) {
        this.onManagementOperationFailed = callback;
    }

    public void setOnUpdateGroupName(Consumer<UpdateGroupNameEvent> callback) {
        this.onUpdateGroupName = callback;
    }

    public void setOnFileTransferProgress(Consumer<FileTransferProgressEvent> callback) {
        this.onFileTransferProgress = callback;
    }
}