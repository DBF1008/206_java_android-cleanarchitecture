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
package com.fernandocejas.android10.sample.presentation.view.fragment;

import android.os.Bundle;
import com.fernandocejas.android10.sample.presentation.ApplicationStub;
import com.fernandocejas.android10.sample.presentation.BuildConfig;
import com.fernandocejas.android10.sample.presentation.model.UserModel;
import com.fernandocejas.android10.sample.presentation.presenter.UserDetailsPresenter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression tests for the state-recovery logic of {@link UserDetailsFragment}.
 *
 * The fragment uses {@code setRetainInstance(true)}, so the three recreation scenarios must behave
 * consistently and the decision to (re)load has to be driven by whether the user is still held in
 * memory, NOT by whether {@code savedInstanceState} is {@code null}:
 *
 * <ul>
 *   <li>first launch: no data in memory -> load</li>
 *   <li>configuration change / fragment retain: data kept in memory -> re-render, no reload</li>
 *   <li>process death + recreation: data gone but savedInstanceState non-null -> load (the bug)</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, application = ApplicationStub.class, sdk = 21)
public class UserDetailsFragmentTest {

  private static final int FAKE_USER_ID = 11;

  private UserDetailsPresenter mockUserDetailsPresenter;

  @Before
  public void setUp() {
    mockUserDetailsPresenter = mock(UserDetailsPresenter.class);
  }

  @Test
  public void testReloadsWhenNoDataInMemoryEvenWithSavedInstanceState() {
    // Simulates process-death recreation: a brand new fragment instance whose data is gone, but
    // for which the system still supplies a non-null savedInstanceState.
    final UserDetailsFragment fragment = UserDetailsFragment.forUser(FAKE_USER_ID);
    fragment.userDetailsPresenter = mockUserDetailsPresenter;

    fragment.onViewCreated(null, new Bundle());

    verify(mockUserDetailsPresenter).setView(fragment);
    verify(mockUserDetailsPresenter).initialize(FAKE_USER_ID);
  }

  @Test
  public void testReloadsOnFirstCreationWithoutSavedInstanceState() {
    final UserDetailsFragment fragment = UserDetailsFragment.forUser(FAKE_USER_ID);
    fragment.userDetailsPresenter = mockUserDetailsPresenter;

    fragment.onViewCreated(null, null);

    verify(mockUserDetailsPresenter).initialize(FAKE_USER_ID);
  }

  @Test
  public void testReRendersWithoutReloadingWhenDataKeptInMemory() {
    // Simulates a configuration change / retained fragment: the user is still in memory.
    final RecordingUserDetailsFragment fragment = new RecordingUserDetailsFragment();
    fragment.userDetailsPresenter = mockUserDetailsPresenter;
    fragment.userModel = new UserModel(FAKE_USER_ID);

    fragment.onViewCreated(null, new Bundle());

    verify(mockUserDetailsPresenter, never()).initialize(anyInt());
    assertTrue("Expected the retained user to be re-rendered", fragment.renderUserCalled);
  }

  /**
   * Test double that records re-render calls without touching the real (unbound) widgets, so the
   * recovery decision can be asserted in a plain JVM unit test.
   */
  public static class RecordingUserDetailsFragment extends UserDetailsFragment {
    boolean renderUserCalled;

    @Override public void renderUser(UserModel user) {
      this.renderUserCalled = true;
    }
  }
}
