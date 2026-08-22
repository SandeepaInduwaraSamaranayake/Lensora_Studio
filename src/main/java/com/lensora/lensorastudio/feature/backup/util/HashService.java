package com.lensora.lensorastudio.feature.backup.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashService
{
    private HashService() {}

    public static String sha256(Path file) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file))
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1)
                {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IOException("SHA-256 not available", e);
        }
    }
}