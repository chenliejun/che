/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.selenium.core.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import javax.inject.Named;

/** @author Dmytro Nochevnov */
@Singleton
public class CheTestOfflineToAccessTokenExchangeApiEndpointUrlProvider
    implements TestOfflineToAccessTokenExchangeApiEndpointUrlProvider {
  @Inject
  @Named("che.offline.to.access.token.exchange.endpoint")
  private String offlineToAccessTokenExchangeApiEndpointUrl;

  @Override
  public URL get() {
    try {
      return new URL(offlineToAccessTokenExchangeApiEndpointUrl);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
