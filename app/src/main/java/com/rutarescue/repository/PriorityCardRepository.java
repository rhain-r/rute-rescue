package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.priority.PriorityCard;

/**
 * Residents' priority IDs (priorityIds/{code}). The owner creates and reads their own; a
 * moderator can read ONE card by its code (after scanning it) but never list them, and can only
 * change the check fields, with an audit event.
 */
public interface PriorityCardRepository {

    /** Saves a newly issued card (the code is the document id). */
    void create(PriorityCard card, Callback<Void> callback);

    /** Follows the signed-in resident's own card; delivers null when they have none. */
    ListenerHandle listenMine(String ownerUid, Callback<PriorityCard> callback);

    /** Reads one card by its code (moderator after a scan); fails when there is no such card. */
    void get(String code, Callback<PriorityCard> callback);

    /** Saves a moderator's check and its audit event together. */
    void saveCheck(PriorityCard card, AuditEvent audit, Callback<Void> callback);
}
