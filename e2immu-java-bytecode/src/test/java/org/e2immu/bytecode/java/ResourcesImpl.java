package org.e2immu.bytecode.java;

import org.e2immu.language.inspection.api.resource.Resources;
import org.e2immu.language.inspection.api.resource.SourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

public class ResourcesImpl implements Resources  {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesImpl.class);

    static class ResourceAccessException extends RuntimeException {
        public ResourceAccessException(String msg) {
            super(msg);
        }
    }

    private final Trie<URI> data = new Trie<>();

    public int addJmod(URL jmodUrl) throws IOException {
        JarURLConnection jarConnection = (JarURLConnection) jmodUrl.openConnection();
        JarFile jarFile = jarConnection.getJarFile();
        AtomicInteger entries = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        jarFile.stream()
                .filter(je -> je.getRealName().startsWith("classes/"))
                .forEach(je -> {
                    String realName = je.getRealName().substring("classes/".length());
                    LOGGER.trace("Adding {}", realName);
                    String[] split = realName.split("/");
                    try {
                        URL fullUrl = new URL(jmodUrl, je.getRealName());
                        data.add(split, fullUrl.toURI());
                        entries.incrementAndGet();
                    } catch (MalformedURLException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                });
        if (errors.get() > 0) {
            throw new IOException("Got " + errors.get() + " errors while adding from jar to classpath");
        }
        return entries.get();
    }

    @Override
    public SourceFile fqnToPath(String fqn, String extension) {
        String[] splitDot = fqn.split("\\.");
        for (int i = 1; i < splitDot.length; i++) {
            String[] parts = new String[i + 1];
            System.arraycopy(splitDot, 0, parts, 0, i);
            parts[i] = splitDot[i];
            for (int j = i + 1; j < splitDot.length; j++) {
                parts[i] += "$" + splitDot[j];
            }
            parts[i] += extension;
            List<URI> uris = data.get(parts);
            if (uris != null && !uris.isEmpty()) {
                return new SourceFile(String.join("/", parts), uris.get(0));
            }
        }
        LOGGER.debug("Cannot find {} with extension {} in classpath", fqn, extension);
        return null;
    }

    @Override
    public byte[] loadBytes(String path) {
        String[] prefix = path.split("/");
        List<URI> urls = data.get(prefix);
        if (urls != null) {
            for (URI uri : urls) {
                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                     InputStream inputStream = uri.toURL().openStream()) {
                    inputStream.transferTo(byteArrayOutputStream);
                    return byteArrayOutputStream.toByteArray();
                } catch (IOException e) {
                    throw new ResourceAccessException("URL = " + uri + ", Cannot read? " + e.getMessage());
                }
            }
        }
        LOGGER.debug("{} not found in class path", path);
        return null;
    }
}
