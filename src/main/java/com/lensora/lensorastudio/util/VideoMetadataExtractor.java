package com.lensora.lensorastudio.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.lensora.lensorastudio.model.MediaMetadata;
import com.lensora.lensorastudio.services.AppSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Invokes the ffprobe binary (part of FFmpeg) to extract video/audio stream
 * metadata as JSON, then flattens it into MediaMetadata groups.
 *
 * ffprobe is NOT bundled — it must be installed and resolvable via PATH,
 * or its full path configured in AppSettings (getFfprobePath()).
 */
public final class VideoMetadataExtractor
{
    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataExtractor.class);
    private static final int TIMEOUT_SECONDS = 15;

    private VideoMetadataExtractor() {}

    public static MediaMetadata extract(File videoFile)
    {
        MediaMetadata result = new MediaMetadata(videoFile.getAbsolutePath(), MediaMetadata.MediaType.VIDEO);

        result.put("File", "Name", videoFile.getName());
        result.put("File", "Size", FileSizeFormatter.formatFileSize(videoFile.length()));
        result.put("File", "Path", videoFile.getAbsolutePath());

        String ffprobePath = resolveFfprobePath();

        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                videoFile.getAbsolutePath()
        );
        pb.redirectErrorStream(false);

        try
        {
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                result.put("Error", "Message", "ffprobe timed out after " + TIMEOUT_SECONDS + "s");
                return result;
            }

            if (process.exitValue() != 0)
            {
                result.put("Error", "Message", "ffprobe exited with code " + process.exitValue()
                        + " — is FFmpeg installed and on PATH?");
                return result;
            }

            parseJson(output.toString(), result);
        }
        catch (IOException e)
        {
            logger.error("[VideoMetadataExtractor] Failed to run ffprobe for {}", videoFile.getName(), e);
            result.put("Error", "Message", "Could not launch ffprobe: " + e.getMessage()
                    + ". Ensure FFmpeg is installed and ffprobe is on PATH.");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            result.put("Error", "Message", "Metadata extraction was interrupted.");
        }

        return result;
    }

    private static void parseJson(String json, MediaMetadata result)
    {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // ── format (container-level: duration, bitrate, size, format name) ──
        if (root.has("format"))
        {
            JsonObject format = root.getAsJsonObject("format");
            for (Map.Entry<String, JsonElement> entry : format.entrySet())
            {
                if (entry.getKey().equals("tags"))
                {
                    JsonObject tags = entry.getValue().getAsJsonObject();
                    for (Map.Entry<String, JsonElement> tag : tags.entrySet())
                    {
                        result.put("Format Tags", tag.getKey(), tag.getValue().getAsString());
                    }
                }
                else
                {
                    result.put("Format", entry.getKey(), entry.getValue().toString().replaceAll("^\"|\"$", ""));
                }
            }
        }

        // ── streams (video/audio/subtitle tracks) ──
        if (root.has("streams"))
        {
            JsonArray streams = root.getAsJsonArray("streams");
            for (int i = 0; i < streams.size(); i++)
            {
                JsonObject stream = streams.get(i).getAsJsonObject();
                String codecType = stream.has("codec_type") ? stream.get("codec_type").getAsString() : "unknown";
                String groupName = capitalize(codecType) + " Stream " + i;

                for (Map.Entry<String, JsonElement> entry : stream.entrySet())
                {
                    if (entry.getKey().equals("tags"))
                    {
                        JsonObject tags = entry.getValue().getAsJsonObject();
                        for (Map.Entry<String, JsonElement> tag : tags.entrySet())
                        {
                            result.put(groupName + " Tags", tag.getKey(), tag.getValue().getAsString());
                        }
                    }
                    else if (entry.getValue().isJsonPrimitive() || entry.getValue().isJsonNull())
                    {
                        String value = entry.getValue().isJsonNull()
                                ? "" : entry.getValue().toString().replaceAll("^\"|\"$", "");
                        result.put(groupName, entry.getKey(), value);
                    }
                }
            }
        }
    }

    private static String capitalize(String s)
    {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String resolveFfprobePath()
    {
        String configured = AppSettings.getInstance().getFfprobePath();
        return (configured != null && !configured.isBlank()) ? configured : "ffprobe";
    }

    public static boolean isSupportedVideo(File file)
    {
        String ext = getExtension(file);
        return ext != null && (ext.equals("mp4") || ext.equals("mov") || ext.equals("avi") || ext.equals("mkv")
                || ext.equals("m4v") || ext.equals("wmv") || ext.equals("webm") || ext.equals("mts")
                || ext.equals("m2ts") || ext.equals("flv"));
    }

    private static String getExtension(File file)
    {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : null;
    }
}