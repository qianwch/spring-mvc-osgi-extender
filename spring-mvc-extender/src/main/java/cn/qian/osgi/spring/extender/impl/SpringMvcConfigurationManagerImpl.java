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
import cn.qian.osgi.spring.extender.api.SpringMvcConstants;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import org.eclipse.gemini.blueprint.io.OsgiBundleResourcePatternResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.http.context.ServletContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

@SuppressWarnings({"unchecked", "SpellCheckingInspection", "BusyWait"})
public class SpringMvcConfigurationManagerImpl implements SpringMvcConfigurationManager {
  private static final Logger log =
    LoggerFactory.getLogger(SpringMvcConfigurationManagerImpl.class);
  private final Bundle extenderBundle;

  public SpringMvcConfigurationManagerImpl(Bundle bnd) {
    this.extenderBundle = bnd;
  }

  @Override
  public void destroySpringMvcConfig(Bundle bnd) {
    if (mvcEnabled(bnd)) {
      ServletContext servletContext = getOrCreateServletContext(bnd, false);
      if (servletContext != null) {
        GenericWebApplicationContext appCtx =
          (GenericWebApplicationContext) servletContext.getAttribute(getSpringContextName(bnd));
        if (appCtx != null) {
          log.info("Shutting down spring context: {} ...", getSpringContextName(bnd));
          servletContext.removeAttribute(getSpringContextName(bnd));
          appCtx.close();
        }
        ServiceRegistration<Servlet> serlvetReg =
          (ServiceRegistration<Servlet>) servletContext.getAttribute(getDispatcherName(bnd));
        if (serlvetReg != null) {
          log.info("Removing dispatcher servlet: {} ...", getDispatcherName(bnd));
          servletContext.removeAttribute(getDispatcherName(bnd));
          serlvetReg.unregister();
        }
        ConfigurableApplicationContext rootCtx =
          (ConfigurableApplicationContext) servletContext.getAttribute(
            getSpringRootContextName(bnd));
        if (rootCtx != null) {
          log.info("Shutting down spring root context: {} ...", getSpringRootContextName(bnd));
          servletContext.removeAttribute(getSpringRootContextName(bnd));
          rootCtx.close();
        }
        ServiceRegistration<ServletContext> servletContextReg =
          (ServiceRegistration<ServletContext>) servletContext.getAttribute(
            getServletContextName(bnd));
        if (servletContextReg != null && !servletContextInUse(servletContext)) {
          destroyServletContext(bnd, servletContext, servletContextReg);
        }
      }
    }
  }

  private synchronized void destroyServletContext(Bundle bnd, ServletContext servletContext,
      ServiceRegistration<ServletContext> servletContextReg) {
    servletContext.removeAttribute(getServletContextName(bnd));
    log.info("Removing Servlet Context: {} ...", getServletContextName(bnd));
    servletContextReg.unregister();
  }

  private boolean servletContextInUse(ServletContext context) {
    Enumeration<String> names = context.getAttributeNames();
    boolean inuse = false;
    while (names.hasMoreElements() && !inuse) {
      String name = names.nextElement();
      Object value = context.getAttribute(name);
      if (value instanceof ServiceReference) {
        ServiceReference<?> ref = (ServiceReference<?>) value;
        if ("true".equals(ref.getProperty(SpringMvcConstants.EXTENDER_NAME))) {
          inuse = true;
        }
      }
    }
    return inuse;
  }

  @Override
  public void createSpringMvcConfig(Bundle bnd) {
    if (!mvcEnabled(bnd)) {
      log.debug("{} is not a spring mvc bundle.", bnd.getSymbolicName());
    } else if (bnd.getState() != Bundle.ACTIVE) {
      log.info("Bundle {} is not in active status.", bnd.getSymbolicName());
    } else {
      ServletContext servletContext = getOrCreateServletContext(bnd, true);
      if (servletContext != null) {
        ConfigurableApplicationContext rootCtx = getOrCreateSpringRootContext(bnd, servletContext);
        GenericWebApplicationContext appCtx = getOrCreateSpringContext(bnd, servletContext);
        if (rootCtx != null) {
          appCtx.setParent(rootCtx);
          rootCtx.refresh();
        }
        getOrCreateDispacher(bnd, servletContext, appCtx);
      }
    }
  }

  @Override
  public void destroySpringMvcConfig(long bndId) {
    destroySpringMvcConfig(extenderBundle.getBundleContext().getBundle(bndId));
  }

  @Override
  public void createSpringMvcConfig(long bndId) {
    createSpringMvcConfig(extenderBundle.getBundleContext().getBundle(bndId));
  }

  private String getSpringContextName(Bundle bnd) {
    return SpringMvcConstants.CONTEXT_NAME_PREFIX + bnd.getSymbolicName();
  }

  private String getSpringRootContextName(Bundle bnd) {
    return SpringMvcConstants.ROOT_CONTEXT_NAME_PREFIX + bnd.getSymbolicName();
  }

  private String getServletContextName(Bundle bnd) {
    return SpringMvcConstants.SERVLET_CONTEXT_NAME_PREFIX + bnd.getSymbolicName();
  }

  private String getDispatcherName(Bundle bnd) {
    return SpringMvcConstants.DISPATCHER_NAME_PREFIX + bnd.getSymbolicName();
  }

  private boolean mvcEnabled(Bundle bnd) {
    return Boolean.parseBoolean(bnd.getHeaders().get(SpringMvcConstants.ENABLED));
  }

  private synchronized ServletContext getOrCreateServletContext(Bundle bnd, boolean waitForReady) {
    BundleContext bndCtx = bnd.getBundleContext();
    String ctxPath = bnd.getHeaders().get(SpringMvcConstants.CONTEXT_ROOT);
    if (ctxPath == null) {
      ctxPath = "/";
    } else if (ctxPath.endsWith("/") && !"/".equals(ctxPath)) {
      ctxPath = ctxPath.substring(0, ctxPath.length() - 1);
    }

    Collection<ServiceReference<ServletContext>> serviceReferences = Collections.EMPTY_LIST;
    boolean waiting = false;
    ServiceRegistration<ServletContextHelper> registration = null;
    do {
      try {
        serviceReferences = bndCtx.getServiceReferences(ServletContext.class,
          String.format("(osgi.web.contextpath=%s)", ctxPath));
      } catch (Exception e) {
        log.error("Unexpected", e);
      }
      if (serviceReferences.size() == 0 && waitForReady) {
        if (!waiting) {
          if (!"/".equals(ctxPath)) {
            Dictionary<String, String> props = new Hashtable<>();
            props.put(HTTP_WHITEBOARD_CONTEXT_NAME, getServletContextName(bnd));
            props.put(HTTP_WHITEBOARD_CONTEXT_PATH, ctxPath);
            props.put(SpringMvcConstants.EXTENDER_NAME, "true");
            registration =
              bndCtx.registerService(ServletContextHelper.class, new ServletContextHelper() {
              }, props);
            log.info("Trying to create ServletContext: {}", ctxPath);
          }
          waiting = true;
        } else {
          try {
            log.info("Waiting for ServletContext({}) to become ready....", ctxPath);
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }
    } while (serviceReferences.size() == 0 && waitForReady);
    if (serviceReferences.size() == 0) {
      return null;
    }
    ServletContext servletContext = bndCtx.getService(serviceReferences.iterator().next());
    if (registration != null) {
      servletContext.setAttribute(getServletContextName(bnd), registration);
    }
    return servletContext;
  }

  private GenericWebApplicationContext getOrCreateSpringContext(Bundle bnd,
    ServletContext servletContext) {
    String springContextName = getSpringContextName(bnd);
    GenericWebApplicationContext appCtx =
      (GenericWebApplicationContext) servletContext.getAttribute(springContextName);
    if (appCtx == null) {
      log.info("Creating Spring Context: {} ......", springContextName);
      OsgiBundleResourcePatternResolver resLoader = new OsgiBundleResourcePatternResolver(bnd);
      appCtx = new GenericWebApplicationContext(servletContext);
      appCtx.setDisplayName(springContextName);
      appCtx.setResourceLoader(resLoader);
      ClassLoader loader = bnd.adapt(BundleWiring.class).getClassLoader();
      appCtx.setClassLoader(loader);
      appCtx.getBeanFactory().registerSingleton("bundleContext", bnd.getBundleContext());
      servletContext.setAttribute(springContextName, appCtx);
      String configCls = bnd.getHeaders().get(SpringMvcConstants.CONTEXT_CONFIG_CLASSES);
      String xmlCfgs = bnd.getHeaders().get(SpringMvcConstants.CONTEXT_XML_LOCATIONS);
      if (configCls != null) {
        log.info("Loading spring context configuration classes for {} .....", springContextName);
        AnnotatedBeanDefinitionReader annotatedBeanDefinitionReader =
          new AnnotatedBeanDefinitionReader(appCtx);
        String[] cfgs = configCls.split(",");
        Arrays.stream(cfgs).forEach(c -> {
          try {
            annotatedBeanDefinitionReader.register(bnd.loadClass(c.trim()));
          } catch (Exception e) {
            log.error("Failed to load {}", c, e);
          }
        });
      }
      if (configCls == null && xmlCfgs == null) {
        log.info(
          "No context configuration specified for {}, will use classpath:/META-INF/spring/*.xml ......",
          springContextName);
        xmlCfgs = "classpath:/META-INF/spring/*.xml";
      }
      if (xmlCfgs != null) {
        log.info("Loading spring context xml configuration files for {} .....", springContextName);
        XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(appCtx);
        String[] xmls = xmlCfgs.split(",");
        xmlBeanDefinitionReader.loadBeanDefinitions(xmls);
      }
    } else {
      log.info("Spring Context {} is already running.", getSpringContextName(bnd));
    }
    return appCtx;
  }

  private ConfigurableApplicationContext getOrCreateSpringRootContext(Bundle bnd,
    ServletContext servletContext) {
    String rootContextName = getSpringRootContextName(bnd);
    String configCls = bnd.getHeaders().get(SpringMvcConstants.ROOT_CONTEXT_CONFIG_CLASSES);
    String xmlCfgs = bnd.getHeaders().get(SpringMvcConstants.ROOT_CONTEXT_XML_LOCATIONS);
    if (configCls == null && xmlCfgs == null) {
      return null;
    }
    GenericApplicationContext rootCtx =
      (GenericApplicationContext) servletContext.getAttribute(rootContextName);
    if (rootCtx == null) {
      log.info("Creating Spring Root Context: {} ......", rootContextName);
      OsgiBundleResourcePatternResolver resLoader = new OsgiBundleResourcePatternResolver(bnd);
      rootCtx = new GenericWebApplicationContext(servletContext);
      rootCtx.setDisplayName(rootContextName);
      rootCtx.setResourceLoader(resLoader);
      ClassLoader loader = bnd.adapt(BundleWiring.class).getClassLoader();
      rootCtx.setClassLoader(loader);
      rootCtx.getBeanFactory().registerSingleton("bundleContext", bnd.getBundleContext());
      servletContext.setAttribute(rootContextName, rootCtx);
      if (configCls != null) {
        log.info("Loading spring root context configuration classes for {} .....", rootContextName);
        AnnotatedBeanDefinitionReader annotatedBeanDefinitionReader =
          new AnnotatedBeanDefinitionReader(rootCtx);
        String[] cfgs = configCls.split(",");
        Arrays.stream(cfgs).forEach(c -> {
          try {
            annotatedBeanDefinitionReader.register(bnd.loadClass(c.trim()));
          } catch (Exception e) {
            log.error("Failed to load {}", c, e);
          }
        });
      }
      if (xmlCfgs != null) {
        log.info("Loading spring root context xml configuration files for {} .....",
          rootContextName);
        XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(rootCtx);
        String[] xmls = xmlCfgs.split(",");
        xmlBeanDefinitionReader.loadBeanDefinitions(xmls);
      }
    } else {
      log.info("Spring Root Context {} is already running.", getSpringContextName(bnd));
    }
    return rootCtx;
  }

  private void getOrCreateDispacher(Bundle bnd, ServletContext servletContext,
    GenericWebApplicationContext appCtx) {
    BundleContext bndCtx = bnd.getBundleContext();
    String servletName = getDispatcherName(bnd);
    Collection<ServiceReference<Servlet>> serviceReferences = Collections.EMPTY_LIST;
    try {
      serviceReferences = bndCtx.getServiceReferences(Servlet.class,
        String.format("(osgi.http.whiteboard.servlet.name=%s)", servletName));
    } catch (InvalidSyntaxException e) {
      log.error("Unexpected", e);
    }
    if (serviceReferences.size() == 0) {
      String servletPattern = bnd.getHeaders().get(SpringMvcConstants.SERVLET_PATTERN);
      if (servletPattern == null) {
        servletPattern = "/*";
      } else if (!servletPattern.endsWith("*")) {
        if (!servletPattern.endsWith("/")) {
          servletPattern += "/";
        }
        servletPattern += "*";
      }
      DispatcherServlet dispatcherServlet = new DispatcherServlet();
      dispatcherServlet.setApplicationContext(appCtx);
      Dictionary<String, String> props = new Hashtable<>();
      props.put(HTTP_WHITEBOARD_SERVLET_PATTERN, servletPattern);
      props.put(HTTP_WHITEBOARD_SERVLET_NAME, servletName);
      props.put(HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED, "true");
      props.put(HTTP_WHITEBOARD_CONTEXT_SELECT,
        String.format("(osgi.http.whiteboard.context.name=%s)",
          servletContext.getServletContextName()));
      props.put(SpringMvcConstants.EXTENDER_NAME, "true");
      ServiceRegistration<Servlet> servletServiceRegistration =
        bndCtx.registerService(Servlet.class, dispatcherServlet, props);
      servletContext.setAttribute(getDispatcherName(bnd), servletServiceRegistration);
    }
  }

  @Override
  public void scanAndLoadSpringMvcConfigs() {
    Arrays.stream(extenderBundle.getBundleContext().getBundles())
      .forEach(this::createSpringMvcConfig);
  }

  @Override
  public void destroyAllSpringMvcConfigs() {
    Arrays.stream(extenderBundle.getBundleContext().getBundles())
      .forEach(this::destroySpringMvcConfig);
  }

  @Override
  public Collection<ConfigurableApplicationContext> listSpringContexts() {
    Collection<ServletContext> servletContexts = listServletContexts();
    Set<ConfigurableApplicationContext> applicationContexts = new HashSet<>();
    servletContexts.forEach(ctx -> applicationContexts.addAll(listSpringContexts(ctx)));
    return applicationContexts;
  }

  @Override
  public Collection<ConfigurableApplicationContext> listSpringContexts(
    ServletContext servletContext) {
    Set<ConfigurableApplicationContext> applicationContexts = new HashSet<>();
    Enumeration<String> names = servletContext.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (servletContext.getAttribute(name) instanceof ConfigurableApplicationContext) {
        applicationContexts.add((ConfigurableApplicationContext) servletContext.getAttribute(name));
      }
    }
    return applicationContexts;
  }

  @Override
  public Collection<ServletContext> listServletContexts() {
    Set<ServletContext> servletContexts = new HashSet<>();
    BundleContext bndCtx = extenderBundle.getBundleContext();
    Collection<ServiceReference<ServletContext>> serviceReferences = Collections.EMPTY_LIST;
    try {
      serviceReferences =
        bndCtx.getServiceReferences(ServletContext.class, null);
    } catch (InvalidSyntaxException e) {
      log.error("Unexpected Exception", e);
    }
    serviceReferences.forEach(sr -> {
      ServletContext servletContext = bndCtx.getService(sr);
      Enumeration<String> names = servletContext.getAttributeNames();
      while (names.hasMoreElements()) {
        String name = names.nextElement();
        if (name.startsWith(SpringMvcConstants.DISPATCHER_NAME_PREFIX)) {
          servletContexts.add(servletContext);
          break;
        }
      }
    });
    return servletContexts;
  }
}
