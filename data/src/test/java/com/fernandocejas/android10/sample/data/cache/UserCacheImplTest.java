/**
 * Copyright (C) 2015 Fernando Cejas Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fernandocejas.android10.sample.data.cache;

import com.fernandocejas.android10.sample.data.ApplicationTestCase;
import com.fernandocejas.android10.sample.data.cache.serializer.Serializer;
import com.fernandocejas.android10.sample.data.entity.UserEntity;
import com.fernandocejas.android10.sample.domain.executor.ThreadExecutor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UserCacheImplTest extends ApplicationTestCase {

  private static final int FAKE_USER_ID = 1;
  private static final String USER_CACHE_DIR_NAME = "user_cache";

  private UserCacheImpl userCache;
  private FileManager fileManager;
  private File appCacheDir;
  private File userCacheDir;

  @Before
  public void setUp() {
    fileManager = new FileManager();
    Serializer serializer = new Serializer();
    // Synchronous executor: runs tasks immediately on the calling thread
    ThreadExecutor synchronousExecutor = Runnable::run;

    userCache = new UserCacheImpl(context(), serializer, fileManager, synchronousExecutor);

    appCacheDir = context().getCacheDir();
    userCacheDir = new File(appCacheDir, USER_CACHE_DIR_NAME);
  }

  @After
  public void tearDown() {
    if (userCacheDir != null && userCacheDir.exists()) {
      fileManager.clearDirectory(userCacheDir);
    }
    // Also clean up any stray files we placed directly in the app cache root for testing
    if (appCacheDir != null) {
      fileManager.clearDirectory(appCacheDir);
    }
  }

  @Test
  public void testCacheDirIsIsolatedSubdirectory() {
    // The user cache should live inside a "user_cache" subdirectory, not the app cache root.
    assertThat(userCacheDir.exists(), is(true));
    assertThat(userCacheDir.getAbsolutePath(),
        is(appCacheDir.getAbsolutePath() + File.separator + USER_CACHE_DIR_NAME));
  }

  @Test
  public void testPutCreatesFileInSubdirectory() {
    UserEntity userEntity = createFakeUserEntity(FAKE_USER_ID);

    userCache.put(userEntity);

    File expectedFile = new File(userCacheDir, "user_" + FAKE_USER_ID);
    assertThat(expectedFile.exists(), is(true));
    assertThat(userCache.isCached(FAKE_USER_ID), is(true));
  }

  @Test
  public void testIsCachedReturnsFalseWhenNotCached() {
    assertThat(userCache.isCached(999), is(false));
  }

  @Test
  public void testEvictAllDoesNotDeleteFilesOutsideUserCacheDir() throws IOException {
    // Place a "foreign" file directly in the app cache root (simulating AutoLoadImageView or
    // any other module that shares the app cache directory).
    File foreignFile = new File(appCacheDir, "image_foreign_cache_file");
    writeContentToFile(foreignFile, "foreign image data");
    assertThat(foreignFile.exists(), is(true));

    // Put a user entity into the cache
    UserEntity userEntity = createFakeUserEntity(FAKE_USER_ID);
    userCache.put(userEntity);
    assertThat(userCache.isCached(FAKE_USER_ID), is(true));

    // Evict all user cache entries
    userCache.evictAll();

    // The user cache file should be deleted
    assertThat(userCache.isCached(FAKE_USER_ID), is(false));

    // The foreign file in the app cache root must survive
    assertThat("Foreign files in the app cache root must not be deleted by evictAll()",
        foreignFile.exists(), is(true));
  }

  @Test
  public void testEvictAllOnlyClearsUserCacheSubdirectory() throws IOException {
    // Create several foreign files in the app cache root
    File foreignFile1 = new File(appCacheDir, "image_cache_abc");
    File foreignFile2 = new File(appCacheDir, "some_other_module_cache");
    writeContentToFile(foreignFile1, "data1");
    writeContentToFile(foreignFile2, "data2");

    // Put multiple users into the cache
    userCache.put(createFakeUserEntity(1));
    userCache.put(createFakeUserEntity(2));
    userCache.put(createFakeUserEntity(3));

    assertThat(userCache.isCached(1), is(true));
    assertThat(userCache.isCached(2), is(true));
    assertThat(userCache.isCached(3), is(true));

    // Evict all
    userCache.evictAll();

    // All user cache entries should be gone
    assertThat(userCache.isCached(1), is(false));
    assertThat(userCache.isCached(2), is(false));
    assertThat(userCache.isCached(3), is(false));

    // Foreign files must be untouched
    assertThat("Foreign file 1 must survive evictAll()", foreignFile1.exists(), is(true));
    assertThat("Foreign file 2 must survive evictAll()", foreignFile2.exists(), is(true));
  }

  @Test
  public void testIsExpiredTriggersEvictionWithoutAffectingForeignFiles() throws IOException {
    // Place a foreign file in the app cache root
    File foreignFile = new File(appCacheDir, "image_should_survive");
    writeContentToFile(foreignFile, "important image cache");

    // Put a user entity
    userCache.put(createFakeUserEntity(FAKE_USER_ID));

    // Force expiration by setting last update time to 0 (epoch), which guarantees
    // (currentTime - 0) > EXPIRATION_TIME.
    fileManager.writeToPreferences(context(),
        "com.fernandocejas.android10.SETTINGS", "last_cache_update", 0);

    // isExpired() should return true and trigger evictAll()
    boolean expired = userCache.isExpired();
    assertThat(expired, is(true));

    // User cache should have been evicted
    assertThat(userCache.isCached(FAKE_USER_ID), is(false));

    // Foreign file must still exist
    assertThat("isExpired() eviction must not delete foreign files",
        foreignFile.exists(), is(true));
  }

  private UserEntity createFakeUserEntity(int userId) {
    UserEntity userEntity = new UserEntity();
    userEntity.setUserId(userId);
    userEntity.setFullname("John Doe");
    return userEntity;
  }

  private void writeContentToFile(File file, String content) throws IOException {
    FileWriter writer = new FileWriter(file);
    writer.write(content);
    writer.close();
  }
}
