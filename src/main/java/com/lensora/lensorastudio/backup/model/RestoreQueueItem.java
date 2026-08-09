package com.lensora.lensorastudio.backup.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;

/** One row in the Restore tab's queue: a .lsbak file plus its current verification state. */
public class RestoreQueueItem
{
    public enum VerificationState
    {
        NOT_VERIFIED, 
        VERIFYING, 
        VERIFIED_OK, 
        VERIFIED_FAILED
    }

    private final File file;
    private final ObjectProperty<VerificationState> state =
            new SimpleObjectProperty<>(VerificationState.NOT_VERIFIED);
    private final StringProperty message = new SimpleStringProperty("");

    public RestoreQueueItem(File file)
    {
        this.file = file;
    }

    public File getFile() { return file; }

    public ObjectProperty<VerificationState> stateProperty() { return state; }
    public VerificationState getState() { return state.get(); }
    public void setState(VerificationState s) { state.set(s); }

    public StringProperty messageProperty() { return message; }
    public String getMessage() { return message.get(); }
    public void setMessage(String m) { message.set(m); }

    /** Equality/hash based on canonical file path, so the same physical file can never be queued twice. */
    private File canonicalOrSelf()
    {
        try { return file.getCanonicalFile(); }
        catch (Exception e) { return file.getAbsoluteFile(); }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof RestoreQueueItem other)) return false;
        return canonicalOrSelf().equals(other.canonicalOrSelf());
    }

    @Override
    public int hashCode()
    {
        return canonicalOrSelf().hashCode();
    }
}