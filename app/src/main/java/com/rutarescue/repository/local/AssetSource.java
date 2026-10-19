package com.rutarescue.repository.local;

import java.io.IOException;
import java.io.InputStream;

/**
 * Opens a bundled data file by its path inside the app's assets folder.
 *
 * Polymorphism: the app passes Android's AssetManager ({@code assets::open}); the unit tests
 * pass a lambda that reads the same files from app/src/main/assets on the computer, so the
 * hazard repository can be load-tested without a phone.
 */
public interface AssetSource {

    InputStream open(String path) throws IOException;
}
