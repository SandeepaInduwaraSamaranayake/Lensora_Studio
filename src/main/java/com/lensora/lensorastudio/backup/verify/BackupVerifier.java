package com.lensora.lensorastudio.backup.verify;

import com.lensora.lensorastudio.backup.model.BackupManifest;
import com.lensora.lensorastudio.backup.util.HashService;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BackupVerifier
{
    private static final Gson GSON = new Gson();

    private BackupVerifier() {}

    public record VerificationResult(boolean success, String message) {}

    /** Verifies a .lsbak archive: readable as ZIP, manifest present, file count and hashes match. */
    public static VerificationResult verify(File lsbakFile)
    {
        try (ZipFile zip = new ZipFile(lsbakFile))
        {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null)
            {
                return new VerificationResult(false, "manifest.json missing - not a valid Lensora backup.");
            }

            BackupManifest manifest;
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8))
            {
                manifest = GSON.fromJson(reader, BackupManifest.class);
            }

            if (manifest == null || manifest.files == null)
            {
                return new VerificationResult(false, "manifest.json is corrupt or unreadable.");
            }

            if (zip.getEntry("project-data.json") == null)
            {
                return new VerificationResult(false, "project-data.json missing - backup is incomplete.");
            }

            int verified = 0;
            for (BackupManifest.ManifestFileEntry entry : manifest.files)
            {
                ZipEntry fileEntry = zip.getEntry("files/" + entry.relativePath);
                if (fileEntry == null)
                {
                    return new VerificationResult(false, "Missing file in archive: " + entry.relativePath);
                }

                File temp = File.createTempFile("lensora-verify-", ".tmp");
                try
                {
                    Files.copy(zip.getInputStream(fileEntry), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    String actualHash = HashService.sha256(temp.toPath());
                    if (!actualHash.equalsIgnoreCase(entry.sha256))
                    {
                        return new VerificationResult(false, "Hash mismatch for: " + entry.relativePath);
                    }
                    verified++;
                }
                finally
                {
                    temp.delete();
                }
            }

            if (verified != manifest.totalFiles)
            {
                return new VerificationResult(false,
                        "File count mismatch: manifest says " + manifest.totalFiles + ", found " + verified);
            }

            return new VerificationResult(true,
                    "Backup verified successfully - " + verified + " file(s), all hashes match.");
        }
        catch (IOException e)
        {
            return new VerificationResult(false, "Could not read archive: " + e.getMessage());
        }
    }
}