package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.feature.project.model.FileRating;
import com.lensora.lensorastudio.feature.project.repository.FileRatingRepository;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;

import java.io.File;
import java.sql.SQLException;

/**
 * Service for persisting file ratings (0-5 stars) and flags (Favorite/Rejected).
 */
public final class FileRatingService 
{

    private FileRatingService() {}

    public static void setRating(File file, int stars) 
    {
        try 
        {
            FileRating rating = FileRatingRepository.find(file.getAbsolutePath());
            if (rating == null) rating = new FileRating();
            rating.setFilePath(file.getAbsolutePath());
            rating.setRating(stars);
            FileRatingRepository.upsert(rating);
        }
        catch (SQLException e) 
        {
            ErrorHandler.show(null, "Failed to set rating", e);
        }
    }

    public static void setFlag(File file, FileRating.Flag flag) 
    {
        try 
        {
            FileRating rating = FileRatingRepository.find(file.getAbsolutePath());
            if (rating == null) rating = new FileRating();
            rating.setFilePath(file.getAbsolutePath());
            rating.setFlag(flag);
            FileRatingRepository.upsert(rating);
        }
        catch (SQLException e)
        {
            ErrorHandler.show(null, "Failed to set flag", e);
        }
    }
}