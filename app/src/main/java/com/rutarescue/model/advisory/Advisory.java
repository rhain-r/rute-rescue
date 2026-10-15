package com.rutarescue.model.advisory;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.validation.TextRules;

/**
 * A verified community update published by LGU personnel and shown on the resident
 * Home screen and notification inbox.
 */
public class Advisory extends AuditableEntity {

    private String title;
    private String message;
    private AlertLevel level;
    private final MetroManilaCity city;
    private boolean published;
    private long publishedAt;
    private final String authorName;

    public Advisory(String id, String title, String message, AlertLevel level, MetroManilaCity city,
                    boolean published, long publishedAt, String authorName) throws ValidationException {
        super(id, null, false);
        if (city == null) {
            throw new ValidationException("city", "An advisory needs the issuing city.");
        }
        this.city = city;
        this.authorName = authorName == null || authorName.isBlank() ? city.getDisplayName() + " LGU" : authorName.strip();
        applyValidated(title, message, level);
        this.published = published;
        this.publishedAt = publishedAt;
    }

    @Override
    public String getEntityType() {
        return "advisory";
    }

    public void edit(String newTitle, String newMessage, AlertLevel newLevel, String actorUid, long now)
            throws ValidationException {
        applyValidated(newTitle, newMessage, newLevel);
        markUpdated(actorUid, now);
    }

    private void applyValidated(String newTitle, String newMessage, AlertLevel newLevel) throws ValidationException {
        String checkedTitle = TextRules.requiredText("title", newTitle);
        TextRules.checkLength("title", checkedTitle, 3, 80);
        String checkedMessage = TextRules.multilineText("message", newMessage, 5, 600);
        title = checkedTitle;
        message = checkedMessage;
        level = newLevel == null ? AlertLevel.ADVISORY : newLevel;
    }

    public void publish(String actorUid, long now) {
        published = true;
        publishedAt = now;
        markUpdated(actorUid, now);
    }

    public void unpublish(String actorUid, long now) {
        published = false;
        markUpdated(actorUid, now);
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public AlertLevel getLevel() { return level; }
    public MetroManilaCity getCity() { return city; }
    public boolean isPublished() { return published; }
    public long getPublishedAt() { return publishedAt; }
    public String getAuthorName() { return authorName; }
}
