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
package com.fernandocejas.android10.sample.test.presenter;

import android.content.Context;
import com.fernandocejas.android10.sample.domain.User;
import com.fernandocejas.android10.sample.domain.interactor.GetUserDetails;
import com.fernandocejas.android10.sample.domain.interactor.GetUserDetails.Params;
import com.fernandocejas.android10.sample.presentation.mapper.UserModelDataMapper;
import com.fernandocejas.android10.sample.presentation.model.UserModel;
import com.fernandocejas.android10.sample.presentation.presenter.UserDetailsPresenter;
import com.fernandocejas.android10.sample.presentation.view.UserDetailsView;
import io.reactivex.observers.DisposableObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class UserDetailsPresenterTest {

  private static final int USER_ID = 1;

  private UserDetailsPresenter userDetailsPresenter;

  @Mock private Context mockContext;
  @Mock private UserDetailsView mockUserDetailsView;
  @Mock private GetUserDetails mockGetUserDetails;
  @Mock private UserModelDataMapper mockUserModelDataMapper;

  @Captor private ArgumentCaptor<DisposableObserver<User>> observerCaptor;

  @Before
  public void setUp() {
    userDetailsPresenter = new UserDetailsPresenter(mockGetUserDetails, mockUserModelDataMapper);
    userDetailsPresenter.setView(mockUserDetailsView);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUserDetailsPresenterInitialize() {
    given(mockUserDetailsView.context()).willReturn(mockContext);

    userDetailsPresenter.initialize(USER_ID);

    verify(mockUserDetailsView).hideRetry();
    verify(mockUserDetailsView).showLoading();
    verify(mockGetUserDetails).execute(any(DisposableObserver.class), any(Params.class));
  }

  /**
   * Regression: after a configuration change the presenter is retained and
   * already holds cached data for the same user. Calling {@code initialize()}
   * again must render the cached data immediately WITHOUT triggering a
   * redundant network call.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testInitializeRendersCachedDataWithoutRefetching() {
    given(mockUserDetailsView.context()).willReturn(mockContext);

    // First load for USER_ID
    userDetailsPresenter.initialize(USER_ID);

    verify(mockGetUserDetails).execute(observerCaptor.capture(), any(Params.class));
    DisposableObserver<User> observer = observerCaptor.getValue();

    // Simulate successful data arrival
    User fakeUser = new User(USER_ID);
    fakeUser.setFullName("John Sanchez");
    UserModel fakeModel = new UserModel(USER_ID);
    given(mockUserModelDataMapper.transform(fakeUser)).willReturn(fakeModel);

    observer.onNext(fakeUser);
    observer.onComplete();

    // Second initialize for the SAME user (simulates view re-creation after config change)
    userDetailsPresenter.initialize(USER_ID);

    // Use case must NOT be called a second time
    verify(mockGetUserDetails, times(1)).execute(any(DisposableObserver.class), any(Params.class));
    // Cached data must be rendered to the view
    verify(mockUserDetailsView).renderUser(fakeModel);
    // No loading indicator should be shown when rendering from cache (only from first load)
    verify(mockUserDetailsView, times(1)).showLoading();
  }

  /**
   * Regression: when the presenter has cached data for one user but
   * {@code initialize()} is called with a DIFFERENT user ID, a fresh load
   * must be triggered rather than serving stale data.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testInitializeWithDifferentUserIdTriggersFreshLoad() {
    given(mockUserDetailsView.context()).willReturn(mockContext);
    final int OTHER_USER_ID = 42;

    // First load for USER_ID
    userDetailsPresenter.initialize(USER_ID);

    verify(mockGetUserDetails).execute(observerCaptor.capture(), any(Params.class));
    DisposableObserver<User> observer = observerCaptor.getValue();

    // Simulate successful data arrival for USER_ID
    observer.onNext(new User(USER_ID));
    observer.onComplete();

    // Initialize for a DIFFERENT user
    userDetailsPresenter.initialize(OTHER_USER_ID);

    // Use case must be called a second time for the different user
    verify(mockGetUserDetails, times(2)).execute(any(DisposableObserver.class), any(Params.class));
    // Loading indicator must be shown for the fresh load
    verify(mockUserDetailsView, times(2)).showLoading();
  }
}
