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
import java.util.Arrays;
import java.util.Collection;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

  /**
   * Regression: after a configuration change the presenter is retained and
   * already holds cached data. Calling {@code initialize()} again (e.g. from
   * {@code onViewCreated()}) must render the cached data immediately WITHOUT
   * triggering a redundant network call.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testInitializeRendersCachedDataWithoutRefetching() {
    given(mockUserListView.context()).willReturn(mockContext);

    // First load: triggers the use case
    userListPresenter.initialize();

    verify(mockGetUserList).execute(observerCaptor.capture(), any(Void.class));
    DisposableObserver<List<User>> observer = observerCaptor.getValue();

    // Simulate successful data arrival
    List<User> fakeUsers = Arrays.asList(new User(1), new User(2));
    Collection<UserModel> fakeModels = Arrays.asList(new UserModel(1), new UserModel(2));
    given(mockUserModelDataMapper.transform(fakeUsers)).willReturn(fakeModels);

    observer.onNext(fakeUsers);
    observer.onComplete();

    // Second initialize (simulates view re-creation after config change)
    userListPresenter.initialize();

    // Use case must NOT be called a second time
    verify(mockGetUserList, times(1)).execute(any(DisposableObserver.class), any(Void.class));
    // Cached data must be rendered to the view
    verify(mockUserListView).renderUserList(fakeModels);
    // No loading indicator should be shown when rendering from cache
    verify(mockUserListView, times(1)).showLoading();
  }

  /**
   * Regression: after process death the presenter is recreated with no cached
   * data. Calling {@code initialize()} must trigger a full load (loading
   * indicator + use case execution).
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testInitializeWithNoCacheTriggersLoad() {
    given(mockUserListView.context()).willReturn(mockContext);

    userListPresenter.initialize();

    verify(mockUserListView).hideRetry();
    verify(mockUserListView).showLoading();
    verify(mockGetUserList).execute(any(DisposableObserver.class), any(Void.class));
  }
}
