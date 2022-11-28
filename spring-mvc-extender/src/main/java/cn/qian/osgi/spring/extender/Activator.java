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
package cn.qian.osgi.spring.extender;

import cn.qian.osgi.spring.extender.api.SpringMvcConfigurationManager;
import cn.qian.osgi.spring.extender.impl.ServletContextManager;
import cn.qian.osgi.spring.extender.impl.SpringMvcConfigurationListener;
import cn.qian.osgi.spring.extender.impl.SpringMvcConfigurationManagerImpl;
import java.util.Hashtable;
import javax.servlet.ServletContext;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {
  private SpringMvcConfigurationListener configurationListener;
  private ServletContextManager servletContextManager;
  private ServiceTracker<ServletContext, ServletContext> serviceTracker;

  @Override
  public void start(BundleContext bundleContext) {
    servletContextManager = new ServletContextManager(bundleContext);
    serviceTracker = new ServiceTracker<>(bundleContext, ServletContext.class, servletContextManager);
    serviceTracker.open();
    SpringMvcConfigurationManager springMvcConfigurationManager =
        new SpringMvcConfigurationManagerImpl(bundleContext, servletContextManager);
    bundleContext.registerService(SpringMvcConfigurationManager.class, springMvcConfigurationManager,
        new Hashtable<>());
    springMvcConfigurationManager.scanAndLoadSpringMvcConfigs();
    configurationListener =
        new SpringMvcConfigurationListener(springMvcConfigurationManager, servletContextManager);
    bundleContext.addBundleListener(configurationListener);
  }

  @Override
  public void stop(BundleContext bundleContext) {
    if (configurationListener != null) {
      bundleContext.removeBundleListener(configurationListener);
    }
    configurationListener = null;
    if (servletContextManager != null) {
      servletContextManager.shutdown();
      servletContextManager = null;
    }
    if (serviceTracker != null) {
      serviceTracker.close();
      serviceTracker = null;
    }
  }
}
