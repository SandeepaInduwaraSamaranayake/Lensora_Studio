package com.lensora.lensorastudio.feature.project.model;

public class Collection
{
    public enum Type { MANUAL, SMART }

    private int collectionId;
    private String name;
    private String icon;
    private Type type;
    private String smartCriteria; // raw JSON string
    private boolean builtin;

    public int getCollectionId()                { return collectionId; }
    public void setCollectionId(int v)          { collectionId = v; }
    public String getName()                     { return name; }
    public void setName(String v)               { name = v; }
    public String getIcon()                     { return icon; }
    public void setIcon(String v)               { icon = v; }
    public Type getType()                       { return type; }
    public void setType(Type v)                 { type = v; }
    public String getSmartCriteria()            { return smartCriteria; }
    public void setSmartCriteria(String v)      { smartCriteria = v; }
    public boolean isBuiltin()                  { return builtin; }
    public void setBuiltin(boolean v)           { builtin = v; }
}