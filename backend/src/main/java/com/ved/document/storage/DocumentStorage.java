package com.ved.document.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Port for binary file storage. The local-disk adapter is used today; an S3/MinIO adapter can be
 * added later without touching callers.
 */
public interface DocumentStorage {

    /**
     * Stores the content and returns an opaque key used to retrieve it later.
     *
     * @param extension file extension without the dot (e.g. "pdf"), kept for debuggability
     */
    String store(InputStream content, String extension);

    Resource load(String key);

    void delete(String key);
}
