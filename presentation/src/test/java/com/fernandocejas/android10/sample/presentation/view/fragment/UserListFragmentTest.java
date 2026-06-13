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
import com.fernandocejas.android10.sample.presentation.presenter.UserListPresenter;
import com.fernandocejas.android10.sample.presentation.view.adapter.UsersAdapter;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression tests for the state-recovery logic of {@link UserListFragment}.
 *
 * The fragment uses {@code setRetainInstance(true)}, so the three recreation scenarios must behave
 * consistently and the decision to (re)load has to be driven by whether the list is still held in
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
public class UserListFragmentTest {

  private UserListFragment userListFragment;
  private UserListPresenter mockUserListPresenter;
  private UsersAdapter mockUsersAdapter;

  @Before
  public void setUp() {
    userListFragment = new UserListFragment();
    mockUserListPresenter = mock(UserListPresenter.class);
    mockUsersAdapter = mock(UsersAdapter.class);
    userListFragment.userListPresenter = mockUserListPresenter;
    userListFragment.usersAdapter = mockUsersAdapter;
  }

  @Test
  public void testReloadsWhenNoDataInMemoryEvenWithSavedInstanceState() {
    // Simulates process-death recreation: a brand new fragment instance whose data is gone, but
    // for which the system still supplies a non-null savedInstanceState.
    userListFragment.onViewCreated(null, new Bundle());

    verify(mockUserListPresenter).setView(userListFragment);
    verify(mockUserListPresenter).initialize();
  }

  @Test
  public void testReloadsOnFirstCreationWithoutSavedInstanceState() {
    userListFragment.onViewCreated(null, null);

    verify(mockUserListPresenter).initialize();
  }

  @Test
  public void testReRendersWithoutReloadingWhenDataKeptInMemory() {
    // Simulates a configuration change / retained fragment: the list is still in memory.
    final List<UserModel> users = Collections.singletonList(new UserModel(1));
    userListFragment.userModelCollection = users;

    userListFragment.onViewCreated(null, new Bundle());

    verify(mockUserListPresenter, never()).initialize();
    verify(mockUsersAdapter).setUsersCollection(users);
  }
}
