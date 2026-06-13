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
import com.fernandocejas.android10.sample.domain.interactor.GetUserList;
import com.fernandocejas.android10.sample.presentation.mapper.UserModelDataMapper;
import com.fernandocejas.android10.sample.presentation.model.UserModel;
import com.fernandocejas.android10.sample.presentation.presenter.UserListPresenter;
import com.fernandocejas.android10.sample.presentation.view.UserListView;
import io.reactivex.observers.DisposableObserver;
import java.util.Collections;
import java.util.List;
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
public class UserListPresenterTest {

  private UserListPresenter userListPresenter;

  @Mock private Context mockContext;
  @Mock private UserListView mockUserListView;
  @Mock private GetUserList mockGetUserList;
  @Mock private UserModelDataMapper mockUserModelDataMapper;
  @Captor private ArgumentCaptor<DisposableObserver<List<User>>> observerCaptor;

  @Before
  public void setUp() {
    userListPresenter = new UserListPresenter(mockGetUserList, mockUserModelDataMapper);
    userListPresenter.setView(mockUserListView);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUserListPresenterInitialize() {
    given(mockUserListView.context()).willReturn(mockContext);

    userListPresenter.initialize();

    verify(mockUserListView).hideRetry();
    verify(mockUserListView).showLoading();
    verify(mockGetUserList).execute(any(DisposableObserver.class), any(Void.class));
  }

  @Test
  public void testDetachClearsViewAndSubscriptions() {
    userListPresenter.detach();

    verify(mockGetUserList).clear();
    verifyZeroInteractions(mockUserListView);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverCallbacksSafeAfterDetach() {
    given(mockUserListView.context()).willReturn(mockContext);
    userListPresenter.initialize();
    verify(mockGetUserList).execute(observerCaptor.capture(), any(Void.class));
    DisposableObserver<List<User>> capturedObserver = observerCaptor.getValue();

    userListPresenter.detach();

    // Simulate async callbacks firing after view detached — should be no-ops
    capturedObserver.onNext(Collections.<User>emptyList());
    capturedObserver.onError(new RuntimeException("test"));
    capturedObserver.onComplete();

    // No view interactions beyond the initial initialize() calls
    verify(mockUserListView).hideRetry();
    verify(mockUserListView).showLoading();
    verifyNoMoreInteractions(mockUserListView);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testReattachAfterDetachAllowsReload() {
    UserListView newMockView = org.mockito.Mockito.mock(UserListView.class);
    given(newMockView.context()).willReturn(mockContext);

    userListPresenter.detach();
    userListPresenter.setView(newMockView);
    userListPresenter.initialize();

    verify(newMockView).hideRetry();
    verify(newMockView).showLoading();
    verify(mockGetUserList).execute(any(DisposableObserver.class), any(Void.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverOnCompleteHidesLoading() {
    userListPresenter.initialize();
    verify(mockGetUserList).execute(observerCaptor.capture(), any(Void.class));

    observerCaptor.getValue().onComplete();

    verify(mockUserListView).hideLoading();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverOnErrorShowsErrorAndRetry() {
    given(mockUserListView.context()).willReturn(mockContext);
    userListPresenter.initialize();
    verify(mockGetUserList).execute(observerCaptor.capture(), any(Void.class));

    observerCaptor.getValue().onError(new RuntimeException("test error"));

    verify(mockUserListView).hideLoading();
    verify(mockUserListView).showError(any(String.class));
    verify(mockUserListView).showRetry();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testObserverOnNextRendersUserList() {
    User fakeUser = new User(1);
    List<User> fakeUsers = Collections.singletonList(fakeUser);
    UserModel fakeModel = new UserModel(1);
    given(mockUserModelDataMapper.transform(fakeUsers))
        .willReturn(Collections.singletonList(fakeModel));

    userListPresenter.initialize();
    verify(mockGetUserList).execute(observerCaptor.capture(), any(Void.class));

    observerCaptor.getValue().onNext(fakeUsers);

    verify(mockUserModelDataMapper).transform(fakeUsers);
    verify(mockUserListView).renderUserList(Collections.singletonList(fakeModel));
  }

  @Test
  public void testOnUserClickedDelegatesToView() {
    UserModel fakeModel = new UserModel(1);
    userListPresenter.onUserClicked(fakeModel);

    verify(mockUserListView).viewUser(fakeModel);
  }

  @Test
  public void testDestroyDisposesUseCaseAndClearsView() {
    userListPresenter.destroy();

    verify(mockGetUserList).dispose();
  }

  @Test
  public void testDestroyAfterDetachStillDisposes() {
    userListPresenter.detach();
    userListPresenter.destroy();

    verify(mockGetUserList).clear();
    verify(mockGetUserList).dispose();
  }
}
