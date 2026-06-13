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
package com.fernandocejas.android10.sample.data.exception;

/**
 * Exception thrown when a server response cannot be parsed or violates the expected protocol
 * (for example malformed JSON or an invalid endpoint url). It represents a data/protocol failure
 * and must not be confused with a {@link NetworkConnectionException}.
 */
public class DataParseException extends Exception {

  public DataParseException() {
    super();
  }

  public DataParseException(final Throwable cause) {
    super(cause);
  }
}
