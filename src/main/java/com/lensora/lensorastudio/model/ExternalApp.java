package com.lensora.lensorastudio.model;

public class ExternalApp
{
    private String name;
    private String executablePath;

    public ExternalApp() {}

    public ExternalApp(String name, String executablePath)
    {
        this.name = name;
        this.executablePath = executablePath;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExecutablePath() { return executablePath; }
    public void setExecutablePath(String executablePath) { this.executablePath = executablePath; }

    @Override
    public String toString() { return name; }
}