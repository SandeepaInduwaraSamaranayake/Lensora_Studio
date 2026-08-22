package com.lensora.lensorastudio.feature.project.model;

public class FileRating
{
    public enum Flag { NONE, FAVORITE, REJECTED }

    private String filePath;
    private Integer projectId;
    private int rating;
    private Flag flag = Flag.NONE;

    public String getFilePath()                 { return filePath; }
    public void setFilePath(String v)           { filePath = v; }
    public Integer getProjectId()               { return projectId; }
    public void setProjectId(Integer v)         { projectId = v; }
    public int getRating()                      { return rating; }
    public void setRating(int v)                { rating = v; }
    public Flag getFlag()                       { return flag; }
    public void setFlag(Flag v)                 { flag = v; }
}