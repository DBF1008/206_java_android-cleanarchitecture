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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UserCacheImplTest extends ApplicationTestCase {

  private static final int FAKE_USER_ID = 11;

  private UserCacheImpl userCache;
  private FileManager fileManager;

  @Before
  public void setUp() {
    fileManager = new FileManager();
    final Serializer serializer = new Serializer();
    // Execute cache jobs (writes/eviction) inline so assertions observe the result deterministically.
    final ThreadExecutor synchronousExecutor = command -> command.run();
    userCache = new UserCacheImpl(context(), serializer, fileManager, synchronousExecutor);
  }

  @After
  public void tearDown() {
    clearCacheDirContents();
  }

  /**
   * Reproduces the reported bug: an expired user cache must not wipe files belonging to other
   * cache consumers (e.g. the image cache used by AutoLoadImageView) that share the app cache
   * directory. With no user cached, {@code isExpired()} returns true and triggers eviction.
   */
  @Test
  public void testExpiredCacheEvictionDoesNotDeleteOtherCacheConsumerFiles() {
    final File otherConsumerFile = writeAppCacheRootFile("image_sample");
    assertThat(otherConsumerFile.exists(), is(true));

    final boolean expired = userCache.isExpired();

    assertThat(expired, is(true));
    assertThat(otherConsumerFile.exists(), is(true));
  }

  /**
   * Eviction must actually clear the user cache namespace, otherwise the fix would just be a no-op.
   */
  @Test
  public void testEvictAllRemovesCachedUser() {
    cacheFakeUser();
    assertThat(userCache.isCached(FAKE_USER_ID), is(true));

    userCache.evictAll();

    assertThat(userCache.isCached(FAKE_USER_ID), is(false));
  }

  /**
   * The user cache namespace is cleared on eviction while files owned by other cache consumers in
   * the app cache root remain untouched - i.e. no cross-module cache pollution.
   */
  @Test
  public void testEvictAllPreservesOtherCacheFilesWhileClearingUserCache() {
    final File otherConsumerFile = writeAppCacheRootFile("image_avatar");
    cacheFakeUser();
    assertThat(userCache.isCached(FAKE_USER_ID), is(true));
    assertThat(otherConsumerFile.exists(), is(true));

    userCache.evictAll();

    assertThat(userCache.isCached(FAKE_USER_ID), is(false));
    assertThat(otherConsumerFile.exists(), is(true));
  }

  private void cacheFakeUser() {
    final UserEntity userEntity = new UserEntity();
    userEntity.setUserId(FAKE_USER_ID);
    userCache.put(userEntity);
  }

  private File writeAppCacheRootFile(String fileName) {
    final File file = new File(context().getCacheDir(), fileName);
    fileManager.writeToFile(file, "content");
    return file;
  }

  private void clearCacheDirContents() {
    final File[] children = cacheDir().listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
  }

  private static void deleteRecursively(File file) {
    if (file == null || !file.exists()) {
      return;
    }
    final File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    file.delete();
  }
}
