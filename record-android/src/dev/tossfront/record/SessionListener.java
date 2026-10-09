package dev.tossfront.record;

import android.service.notification.NotificationListenerService;

/** Uses listener access only for active media controllers, never notification contents. */
public final class SessionListener extends NotificationListenerService {
    @Override public void onListenerConnected() { SessionRepository.get(this).connect(); }
    @Override public void onListenerDisconnected() { SessionRepository.get(this).disconnected(); }
}
