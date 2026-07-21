package io.github.agorokh.scenariocraft.judge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

final class JudgeImage {
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;

    private final String fileName;
    private final byte[] bytes;

    private JudgeImage(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    static JudgeImage read(Path image, Path allowedRoot) throws IOException {
        BasicFileAttributes before = attributes(image);
        Path realImage = image.toRealPath();
        if (!realImage.startsWith(allowedRoot)) {
            throw new IOException("Judge image escapes its allowed directory: " + image);
        }
        requireSingleLink(image);
        if (before.size() <= 0 || before.size() > MAX_BYTES) {
            throw new IOException("Judge image size is outside the allowed range: "
                    + image.getFileName());
        }

        byte[] bytes;
        try (var input = Files.newInputStream(image, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes((int) MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Judge image exceeds the byte limit: " + image.getFileName());
        }

        BasicFileAttributes after = attributes(image);
        Path realAfter = image.toRealPath();
        requireSingleLink(image);
        if (before.fileKey() == null
                || !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size()
                || bytes.length != before.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !realImage.equals(realAfter)) {
            throw new IOException("Judge image changed while it was being read: " + image);
        }
        validatePng(bytes, image.getFileName());
        return new JudgeImage(image.getFileName().toString(), bytes);
    }

    String fileName() {
        return fileName;
    }

    byte[] bytes() {
        return bytes;
    }

    static void requireSingleLink(Path path) throws IOException {
        Object linkCount;
        try {
            linkCount = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException | IllegalArgumentException exception) {
            throw new IOException(
                    "Filesystem cannot verify judge input link ownership: " + path,
                    exception);
        }
        if (!(linkCount instanceof Number count) || count.longValue() != 1) {
            throw new IOException("Hard-linked judge inputs are not allowed: " + path);
        }
    }

    private static BasicFileAttributes attributes(Path image) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                image, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Judge image must be a regular file: " + image);
        }
        return attributes;
    }

    private static void validatePng(byte[] bytes, Path fileName) throws IOException {
        if (bytes.length < 24
                || (bytes[0] & 0xff) != 0x89
                || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G'
                || bytes[4] != 0x0d || bytes[5] != 0x0a
                || bytes[6] != 0x1a || bytes[7] != 0x0a
                || bytes[12] != 'I' || bytes[13] != 'H'
                || bytes[14] != 'D' || bytes[15] != 'R') {
            throw new IOException("Judge image is not a valid PNG: " + fileName);
        }
        ByteBuffer dimensions = ByteBuffer.wrap(bytes, 16, 8);
        int width = dimensions.getInt();
        int height = dimensions.getInt();
        if (width <= 0 || height <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("Judge image dimensions are outside the allowed range: "
                    + fileName);
        }
    }
}
