package io.github.agorokh.scenariocraft.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

final class BlockColors {
    private final Map<String, String> colors;

    BlockColors() {
        try (InputStream stream = BlockColors.class.getResourceAsStream("/palette-colors.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing palette-colors.json");
            }
            colors = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() {}.getType());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read palette-colors.json", exception);
        }
    }

    Color color(String blockId) {
        String hex = colors.get(blockId);
        if (hex != null) {
            return Color.decode(hex);
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(blockId.getBytes(StandardCharsets.UTF_8));
            int red = 64 + Byte.toUnsignedInt(hash[0]) / 2;
            int green = 64 + Byte.toUnsignedInt(hash[1]) / 2;
            int blue = 64 + Byte.toUnsignedInt(hash[2]) / 2;
            return new Color(red, green, blue);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
