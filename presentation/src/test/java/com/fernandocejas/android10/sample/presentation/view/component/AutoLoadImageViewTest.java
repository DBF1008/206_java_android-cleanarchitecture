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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import com.fernandocejas.android10.sample.presentation.ApplicationStub;
import com.fernandocejas.android10.sample.presentation.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the request-token guard that protects {@link AutoLoadImageView} against
 * stale asynchronous image results overwriting the image of the request currently in effect (for
 * example when the view is recycled for another user, re-bound after a fragment restore, or a slow
 * download returns after a newer one).
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, application = ApplicationStub.class, sdk = 21)
public class AutoLoadImageViewTest {

  private Activity activity;
  private AutoLoadImageView imageView;

  @Before public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).create().get();
    imageView = new AutoLoadImageView(activity);
  }

  @Test public void shouldApplyResultBelongingToTheCurrentRequest() {
    Bitmap bitmap = createBitmap(2);

    imageView.loadBitmap(imageView.currentRequestToken(), bitmap);

    assertThat(displayedBitmap()).isSameAs(bitmap);
  }

  @Test public void shouldIgnoreStaleResultReturningAfterTheCurrentOne() {
    // A first request (e.g. user A) is superseded ...
    long staleToken = imageView.nextRequestToken();
    // ... by a newer request (e.g. the view recycled into user B), now the latest one.
    long latestToken = imageView.nextRequestToken();
    Bitmap latestBitmap = createBitmap(2);
    Bitmap staleBitmap = createBitmap(4);

    // The current request resolves first and is shown.
    imageView.loadBitmap(latestToken, latestBitmap);
    assertThat(displayedBitmap()).isSameAs(latestBitmap);

    // The previous (slow) request returns late and must NOT overwrite the current image.
    imageView.loadBitmap(staleToken, staleBitmap);
    assertThat(displayedBitmap()).isSameAs(latestBitmap);
  }

  @Test public void shouldIgnoreStaleResultReturningBeforeTheCurrentOne() {
    long staleToken = imageView.nextRequestToken();
    long latestToken = imageView.nextRequestToken();
    Bitmap latestBitmap = createBitmap(2);
    Bitmap staleBitmap = createBitmap(4);

    // The stale request returns first: nothing should be displayed for it.
    imageView.loadBitmap(staleToken, staleBitmap);
    assertThat(displayedBitmap()).isNull();

    // The current request returns and is shown.
    imageView.loadBitmap(latestToken, latestBitmap);
    assertThat(displayedBitmap()).isSameAs(latestBitmap);
  }

  @Test public void shouldAdvanceTheRequestTokenForEveryRequest() {
    long initialToken = imageView.currentRequestToken();

    // A null url is a valid request that advances the token without starting a network load.
    imageView.setImageUrl(null);
    long afterFirstRequest = imageView.currentRequestToken();

    imageView.setImageUrl(null);
    long afterSecondRequest = imageView.currentRequestToken();

    assertThat(afterFirstRequest).isGreaterThan(initialToken);
    assertThat(afterSecondRequest).isGreaterThan(afterFirstRequest);
  }

  private static Bitmap createBitmap(int size) {
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
  }

  private Bitmap displayedBitmap() {
    if (imageView.getDrawable() == null) {
      return null;
    }
    return ((BitmapDrawable) imageView.getDrawable()).getBitmap();
  }
}
