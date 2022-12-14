/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package cn.qian.osgi.spring.extender.impl;

import cn.qian.osgi.spring.extender.api.SpringMvcConfigurationManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SpringMvcConfigurationListener implements SynchronousBundleListener {
  private final SpringMvcConfigurationManager springMvcConfigurationManager;
  private final ServletContextManager servletContextManager;

  public SpringMvcConfigurationListener(SpringMvcConfigurationManager springMvcConfigurationManager,
      ServletContextManager servletContextManager) {
    this.springMvcConfigurationManager = springMvcConfigurationManager;
    this.servletContextManager = servletContextManager;
  }

  @Override
  public void bundleChanged(BundleEvent bundleEvent) {
    switch (bundleEvent.getType()) {
      case BundleEvent.INSTALLED:
        servletContextManager.scanBundleForServletContext(bundleEvent.getBundle());
        break;
      case BundleEvent.STARTED:
        springMvcConfigurationManager.createSpringMvcConfig(bundleEvent.getBundle());
        break;
      case BundleEvent.STOPPING:
        springMvcConfigurationManager.destroySpringMvcConfig(bundleEvent.getBundle());
        break;
      default:
        break;
    }
  }
}
