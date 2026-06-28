package com.plantops.scenario.planning.delivery;

import com.plantops.scenario.planning.sandbox.OntologySandboxStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DeliveryPlanningSandboxStore extends OntologySandboxStore<DeliveryPlanningSandbox> {

    private final Map<String, String> sandboxIdByDeliveryKey = new ConcurrentHashMap<>();
    private final Map<String, String> deliveryKeyBySandboxId = new ConcurrentHashMap<>();

    @Override
    protected String notFoundMessage(String sessionId) {
        return "Delivery planning sandbox not found: " + sessionId;
    }

    @Override
    protected String expiredMessage(String sessionId) {
        return "Delivery planning sandbox expired: " + sessionId;
    }

    @Override
    public DeliveryPlanningSandbox put(DeliveryPlanningSandbox session) {
        super.put(session);
        String deliveryKey = deliveryKey(session.workspaceId(), session.deliveryId());
        sandboxIdByDeliveryKey.put(deliveryKey, session.sandboxId());
        deliveryKeyBySandboxId.put(session.sandboxId(), deliveryKey);
        return session;
    }

    @Override
    public void remove(String sessionId) {
        String deliveryKey = deliveryKeyBySandboxId.remove(sessionId);
        if (deliveryKey != null) {
            sandboxIdByDeliveryKey.remove(deliveryKey);
        }
        super.remove(sessionId);
    }

    public DeliveryPlanningSandbox findByDelivery(String workspaceId, String deliveryId) {
        String sandboxId = sandboxIdByDeliveryKey.get(deliveryKey(workspaceId, deliveryId));
        if (sandboxId == null) {
            return null;
        }
        try {
            return require(sandboxId, workspaceId);
        } catch (Exception ex) {
            sandboxIdByDeliveryKey.remove(deliveryKey(workspaceId, deliveryId));
            deliveryKeyBySandboxId.remove(sandboxId);
            return null;
        }
    }

    private static String deliveryKey(String workspaceId, String deliveryId) {
        return workspaceId + "::" + deliveryId;
    }
}
