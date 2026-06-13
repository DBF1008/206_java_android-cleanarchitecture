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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

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

  @Test
  public void testDetachClearsViewAndSubscriptions() {
    userDetailsPresenter.detach();

    verify(mockGetUserDetails).clear();
    verifyZeroInteractions(mockUserDetailsView);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverCallbacksSafeAfterDetach() {
    given(mockUserDetailsView.context()).willReturn(mockContext);
    userDetailsPresenter.initialize(USER_ID);
    verify(mockGetUserDetails).execute(observerCaptor.capture(), any(Params.class));
    DisposableObserver<User> capturedObserver = observerCaptor.getValue();

    userDetailsPresenter.detach();

    // Simulate async callbacks firing after view detached — should be no-ops
    capturedObserver.onNext(new User(USER_ID));
    capturedObserver.onError(new RuntimeException("test"));
    capturedObserver.onComplete();

    // No view interactions beyond the initial initialize() calls
    verify(mockUserDetailsView).hideRetry();
    verify(mockUserDetailsView).showLoading();
    verifyNoMoreInteractions(mockUserDetailsView);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReattachAfterDetachAllowsReload() {
    UserDetailsView newMockView = org.mockito.Mockito.mock(UserDetailsView.class);
    given(newMockView.context()).willReturn(mockContext);

    userDetailsPresenter.detach();
    userDetailsPresenter.setView(newMockView);
    userDetailsPresenter.initialize(USER_ID);

    verify(newMockView).hideRetry();
    verify(newMockView).showLoading();
    verify(mockGetUserDetails).execute(any(DisposableObserver.class), any(Params.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverOnCompleteHidesLoading() {
    userDetailsPresenter.initialize(USER_ID);
    verify(mockGetUserDetails).execute(observerCaptor.capture(), any(Params.class));

    observerCaptor.getValue().onComplete();

    verify(mockUserDetailsView).hideLoading();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverOnErrorShowsErrorAndRetry() {
    given(mockUserDetailsView.context()).willReturn(mockContext);
    userDetailsPresenter.initialize(USER_ID);
    verify(mockGetUserDetails).execute(observerCaptor.capture(), any(Params.class));

    observerCaptor.getValue().onError(new RuntimeException("test error"));

    verify(mockUserDetailsView).hideLoading();
    verify(mockUserDetailsView).showError(any(String.class));
    verify(mockUserDetailsView).showRetry();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverOnNextRendersUser() {
    User fakeUser = new User(USER_ID);
    fakeUser.setFullName("John Doe");
    UserModel fakeModel = new UserModel(USER_ID);
    given(mockUserModelDataMapper.transform(fakeUser)).willReturn(fakeModel);

    userDetailsPresenter.initialize(USER_ID);
    verify(mockGetUserDetails).execute(observerCaptor.capture(), any(Params.class));

    observerCaptor.getValue().onNext(fakeUser);

    verify(mockUserModelDataMapper).transform(fakeUser);
    verify(mockUserDetailsView).renderUser(fakeModel);
  }

  @Test
  public void testDestroyDisposesUseCaseAndClearsView() {
    userDetailsPresenter.destroy();

    verify(mockGetUserDetails).dispose();
  }

  @Test
  public void testDestroyAfterDetachStillDisposes() {
    userDetailsPresenter.detach();
    userDetailsPresenter.destroy();

    verify(mockGetUserDetails).clear();
    verify(mockGetUserDetails).dispose();
  }
}
