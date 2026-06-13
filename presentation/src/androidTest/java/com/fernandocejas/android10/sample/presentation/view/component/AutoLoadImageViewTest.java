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
package com.fernandocejas.android10.sample.presentation.view.component;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import com.fernandocejas.android10.sample.presentation.view.activity.UserDetailsActivity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression tests for the request-token cancellation mechanism in
 * {@link AutoLoadImageView}.
 *
 * <p>These tests verify that when {@link AutoLoadImageView#setImageUrl(String)} is called
 * repeatedly in quick succession, only the result belonging to the <em>latest</em> URL is
 * applied to the view, and any stale background work is either interrupted or discarded via
 * the token check.</p>
 */
public class AutoLoadImageViewTest
    extends ActivityInstrumentationTestCase2<UserDetailsActivity> {

  private static final int FAKE_USER_ID = 10;
  private static final long LATCH_TIMEOUT_SECONDS = 5;

  private Activity activity;

  public AutoLoadImageViewTest() {
    super(UserDetailsActivity.class);
  }

  @Override protected void setUp() throws Exception {
    super.setUp();
    Intent intent = UserDetailsActivity.getCallingIntent(
        getInstrumentation().getTargetContext(), FAKE_USER_ID);
    setActivityIntent(intent);
    activity = getActivity();
  }

  // -----------------------------------------------------------------------
  // isValidRequest() unit tests
  // -----------------------------------------------------------------------

  public void testIsValidRequest_returnsTrueForCurrentToken() throws Exception {
    AutoLoadImageView view = new AutoLoadImageView(activity);
    // The initial token is 0 (no setImageUrl call yet).
    assertTrue("Token 0 should be valid before any setImageUrl call",
        view.isValidRequest(0));
  }

  public void testIsValidRequest_returnsFalseForStaleToken() throws Exception {
    AutoLoadImageView view = new AutoLoadImageView(activity);
    // Simulate two setImageUrl calls by bumping the token twice.
    incrementRequestToken(view); // token = 1
    incrementRequestToken(view); // token = 2

    assertFalse("Token 0 must be stale when current token is 2",
        view.isValidRequest(0));
    assertFalse("Token 1 must be stale when current token is 2",
        view.isValidRequest(1));
    assertTrue("Token 2 must be valid (it is the current token)",
        view.isValidRequest(2));
  }

  // -----------------------------------------------------------------------
  // setImageUrl() token-increment tests
  // -----------------------------------------------------------------------

  public void testSetImageUrl_incrementsToken() throws Exception {
    TestableAutoLoadImageView view = new TestableAutoLoadImageView(activity);
    int initial = getRequestToken(view);

    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/token_test_1.png");
      }
    });
    assertEquals("First setImageUrl must increment token by 1",
        initial + 1, getRequestToken(view));

    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/token_test_2.png");
      }
    });
    assertEquals("Second setImageUrl must increment token again",
        initial + 2, getRequestToken(view));
  }

  public void testSetImageUrl_onlyLatestTokenIsValid() throws Exception {
    TestableAutoLoadImageView view = new TestableAutoLoadImageView(activity);

    final int[] capturedTokens = new int[2];

    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/latest_1.png");
        capturedTokens[0] = getRequestToken(view);
      }
    });

    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/latest_2.png");
        capturedTokens[1] = getRequestToken(view);
      }
    });

    assertFalse("First token must be stale after second setImageUrl",
        view.isValidRequest(capturedTokens[0]));
    assertTrue("Second (latest) token must be valid",
        view.isValidRequest(capturedTokens[1]));
  }

  // -----------------------------------------------------------------------
  // Thread-interruption tests
  // -----------------------------------------------------------------------

  public void testSetImageUrl_interruptsPreviousLoadingThread() throws Exception {
    TestableAutoLoadImageView view = new TestableAutoLoadImageView(activity);

    // Start first load — the controllable downloader will block.
    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/interrupt_1.png");
      }
    });
    assertTrue("First download must have started",
        view.waitForDownloadStart(0, LATCH_TIMEOUT_SECONDS));

    // Grab a reference to the background thread that is blocked in download().
    Thread firstThread = view.getLoadingThread();
    assertNotNull("Background thread must exist after setImageUrl", firstThread);

    // Start second load — this must interrupt the first thread.
    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/interrupt_2.png");
      }
    });

    // Wait for the first thread to actually terminate.
    firstThread.join(TimeUnit.SECONDS.toMillis(LATCH_TIMEOUT_SECONDS));
    assertFalse("First background thread must have been interrupted and terminated",
        firstThread.isAlive());
  }

  // -----------------------------------------------------------------------
  // End-to-end: stale download result is discarded
  // -----------------------------------------------------------------------

  public void testRapidSetImageUrl_staleDownloadResultIsDiscarded() throws Exception {
    TestableAutoLoadImageView view = new TestableAutoLoadImageView(activity);

    // --- Load URL 1 (download will block) ---
    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/slow_1.png");
      }
    });
    assertTrue("Download 0 must have started",
        view.waitForDownloadStart(0, LATCH_TIMEOUT_SECONDS));

    // --- Load URL 2 (interrupts download 0, starts download 1) ---
    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/slow_2.png");
      }
    });
    assertTrue("Download 1 must have started",
        view.waitForDownloadStart(1, LATCH_TIMEOUT_SECONDS));

    // --- Complete download 2 with a real bitmap ---
    Bitmap bitmap2 = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    view.releaseDownload(1, bitmap2);

    // Give the UI thread time to process the posted setImageBitmap.
    Thread.sleep(500);

    // The stale download 0 was interrupted, so its callback never fired.
    // Only bitmap2 should have been applied.
    assertEquals("Exactly one bitmap must have been applied",
        1, view.getAppliedBitmaps().size());
    assertSame("The applied bitmap must be from URL 2",
        bitmap2, view.getAppliedBitmaps().get(0));
  }

  public void testStaleCallbackDoesNotApplyBitmap() throws Exception {
    // This test verifies the token-check path inside the download callback.
    // We manually invoke the callback with a stale token and confirm the
    // bitmap is NOT applied to the view.
    TestableAutoLoadImageView view = new TestableAutoLoadImageView(activity);

    // Bump the token so that token=1 is already stale.
    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/stale_cb_1.png");
      }
    });
    runAndSync(new Runnable() {
      @Override public void run() {
        view.setImageUrl("http://example.com/stale_cb_2.png");
      }
    });

    // At this point, the current token is 2. Token 1 is stale.
    assertFalse("Token 1 must be stale", view.isValidRequest(1));

    // Simulate a late-arriving callback for token 1 by directly calling
    // loadBitmap via reflection (bypassing the thread-interruption layer).
    Bitmap staleBitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
    invokeLoadBitmap(view, staleBitmap, 1); // stale token

    Thread.sleep(200);

    // The stale bitmap must NOT have been applied.
    assertEquals("Stale callback must not apply any bitmap",
        0, view.getAppliedBitmaps().size());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Runs {@code runnable} on the main (UI) thread and blocks until it completes.
   */
  private void runAndSync(Runnable runnable) {
    getInstrumentation().runOnMainSync(runnable);
  }

  /**
   * Reads the current value of the private {@code requestToken} field via reflection.
   */
  private int getRequestToken(AutoLoadImageView view) throws Exception {
    Field field = AutoLoadImageView.class.getDeclaredField("requestToken");
    field.setAccessible(true);
    AtomicInteger token = (AtomicInteger) field.get(view);
    return token.get();
  }

  /**
   * Increments the private {@code requestToken} field via reflection, simulating a
   * {@link AutoLoadImageView#setImageUrl(String)} call without triggering the full load flow.
   */
  private void incrementRequestToken(AutoLoadImageView view) throws Exception {
    Field field = AutoLoadImageView.class.getDeclaredField("requestToken");
    field.setAccessible(true);
    AtomicInteger token = (AtomicInteger) field.get(view);
    token.incrementAndGet();
  }

  /**
   * Invokes the private {@code loadBitmap(Bitmap, int)} method via reflection, allowing
   * tests to simulate late-arriving results with specific token values.
   */
  private void invokeLoadBitmap(AutoLoadImageView view, Bitmap bitmap, int token)
      throws Exception {
    java.lang.reflect.Method method = AutoLoadImageView.class.getDeclaredMethod(
        "loadBitmap", Bitmap.class, int.class);
    method.setAccessible(true);
    method.invoke(view, bitmap, token);
  }

  // -----------------------------------------------------------------------
  // Test doubles
  // -----------------------------------------------------------------------

  /**
   * A {@link AutoLoadImageView} subclass that replaces the real {@link ImageDownloader}
   * with a {@link ControllableDownloader}, forces "internet available", and records every
   * bitmap applied via {@link #setImageBitmap(Bitmap)}.
   */
  private static class TestableAutoLoadImageView extends AutoLoadImageView {

    private final List<ControllableDownloader> downloaders = new ArrayList<ControllableDownloader>();
    private final List<Bitmap> appliedBitmaps = new ArrayList<Bitmap>();

    TestableAutoLoadImageView(Activity activity) {
      super(activity);
    }

    @Override
    ImageDownloader createImageDownloader() {
      ControllableDownloader downloader = new ControllableDownloader();
      synchronized (downloaders) {
        downloaders.add(downloader);
      }
      return downloader;
    }

    @Override
    boolean isThereInternetConnection() {
      return true;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
      super.setImageBitmap(bm);
      synchronized (appliedBitmaps) {
        appliedBitmaps.add(bm);
      }
    }

    List<Bitmap> getAppliedBitmaps() {
      synchronized (appliedBitmaps) {
        return new ArrayList<Bitmap>(appliedBitmaps);
      }
    }

    boolean waitForDownloadStart(int index, long timeoutSeconds) throws InterruptedException {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
      while (true) {
        ControllableDownloader d;
        synchronized (downloaders) {
          if (index < downloaders.size()) {
            d = downloaders.get(index);
          } else {
            d = null;
          }
        }
        if (d != null && d.hasStarted()) {
          return true;
        }
        if (System.currentTimeMillis() >= deadline) {
          return false;
        }
        Thread.sleep(50);
      }
    }

    void releaseDownload(int index, Bitmap result) {
      synchronized (downloaders) {
        downloaders.get(index).release(result);
      }
    }

    Thread getLoadingThread() throws Exception {
      Field field = AutoLoadImageView.class.getDeclaredField("currentLoadingThread");
      field.setAccessible(true);
      return (Thread) field.get(this);
    }
  }

  /**
   * A controllable {@link ImageDownloader} that blocks inside {@link #download} until
   * {@link #release(Bitmap)} is called by the test. This allows precise timing control
   * in race-condition tests.
   */
  private static class ControllableDownloader extends AutoLoadImageView.ImageDownloader {

    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch releaseLatch = new CountDownLatch(1);
    private volatile Bitmap resultBitmap;
    private volatile boolean started = false;

    @Override
    void download(String imageUrl, Callback callback) {
      started = true;
      startLatch.countDown();
      try {
        releaseLatch.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // Thread was interrupted (cancelled by a newer setImageUrl call).
        // Do NOT invoke the callback — this is the correct cancellation behaviour.
        Thread.currentThread().interrupt();
        return;
      }
      if (resultBitmap != null) {
        callback.onImageDownloaded(resultBitmap);
      } else {
        callback.onError();
      }
    }

    boolean hasStarted() {
      return started;
    }

    void release(Bitmap bitmap) {
      this.resultBitmap = bitmap;
      releaseLatch.countDown();
    }
  }
}
