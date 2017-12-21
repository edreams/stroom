package stroom.docstore.server.fs;

import stroom.docstore.server.Persistence;
import stroom.docstore.server.RWLockFactory;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class FSPersistence implements Persistence {
    private static final String FILE_EXTENSION = ".json";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;

    @Inject
    FSPersistence(final FSPersistenceConfig config) {
        try {
            final String path = config.getPath();
            this.dir = Paths.get(path);
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public FSPersistence(final Path dir) {
        try {
            this.dir = dir;
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(final DocRef docRef) {
        final Path filePath = getPath(docRef);
        return Files.isRegularFile(filePath);
    }

    @Override
    public InputStream getInputStream(final DocRef docRef) {
        try {
            final Path path = getPath(docRef);
            return Files.newInputStream(path);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public OutputStream getOutputStream(final DocRef docRef, final boolean update) {
        try {
            final Path filePath = getPath(docRef);
            if (update) {
                if (!Files.isRegularFile(filePath)) {
                    throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
                }
            } else if (Files.isRegularFile(filePath)) {
                throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
            }
            return Files.newOutputStream(filePath);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void delete(final DocRef docRef) {
        try {
            final Path path = getPath(docRef);
            Files.delete(path);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<DocRef> list(final String type) {
        List<DocRef> list;
        try (final Stream<Path> stream = Files.list(getPathForType(type))) {
            list = stream
                    .parallel()
                    .filter(p -> p.toString().endsWith(FILE_EXTENSION))
                    .map(p -> {
                        final String fileName = p.getFileName().toString();
                        final int index = fileName.indexOf(".");
                        final String uuid = fileName.substring(0, index);
                        return new DocRef(type, uuid);
                    })
                    .collect(Collectors.toList());
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return list;
    }

    @Override
    public RWLockFactory getLockFactory() {
        return lockFactory;
    }

    private Path getPath(final DocRef docRef) {
        return getPathForType(docRef.getType()).resolve(docRef.getUuid() + FILE_EXTENSION);
    }

    private Path getPathForType(final String type) {
        final Path path = dir.resolve(type);
        try {
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return path;
    }
}