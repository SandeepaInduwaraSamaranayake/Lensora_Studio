package com.lensora.lensorastudio.feature.backup.verify;

import com.google.gson.Gson;
import com.lensora.lensorastudio.feature.backup.model.BackupManifest;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BackupVerifier
{
    private static final Gson GSON = new Gson();
    private static final int BUFFER_SIZE = 64 * 1024;

    private BackupVerifier() {}

    public record VerificationResult(boolean success, String message) {}

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
                return new VerificationResult(false, "manifest.json is corrupt.");
            }

            if (zip.getEntry("project-data.json") == null)
            {
                return new VerificationResult(false, "project-data.json missing - backup is incomplete.");
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            int verified = 0;

            for (BackupManifest.ManifestFileEntry entry : manifest.files)
            {
                ZipEntry fileEntry = zip.getEntry("files/" + entry.relativePath);
                if (fileEntry == null)
                {
                    return new VerificationResult(false, "Missing file in archive: " + entry.relativePath);
                }

                // Hashes the entry by streaming through it directly - no
                // temp file is ever written to disk, so verifying a
                // multi-hundred-GB backup can't exhaust the system drive.
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream is = zip.getInputStream(fileEntry);
                    DigestInputStream dis = new DigestInputStream(is, digest))
                {
                    while (dis.read(buffer) != -1)
                    {
                        // reading drives the digest; bytes are discarded
                    }
                }

                String actualHash = HexFormat.of().formatHex(digest.digest());
                if (!actualHash.equalsIgnoreCase(entry.sha256))
                {
                    return new VerificationResult(false, "Hash mismatch for: " + entry.relativePath);
                }
                verified++;
            }

            if (verified != manifest.totalFiles)
            {
                return new VerificationResult(false,
                        "File count mismatch: manifest says " + manifest.totalFiles + ", found " + verified);
            }

            return new VerificationResult(true,
                    String.format("Backup verified successfully - %d file(s), all hashes match.", verified));
        }
        catch (Exception e)
        {
            return new VerificationResult(false, "Verification failed: " + e.getMessage());
        }
    }
}