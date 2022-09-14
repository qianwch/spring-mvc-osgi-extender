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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.gemini.blueprint.io.OsgiBundleResourcePatternResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static cn.qian.osgi.spring.extender.impl.ServletContextManager.normalizeCtxPath;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

@SuppressWarnings({"unchecked", "SpellCheckingInspection"})
public class SpringMvcConfigurationManagerImpl implements SpringMvcConfigurationManager {
  private static final Logger log =
      LoggerFactory.getLogger(SpringMvcConfigurationManagerImpl.class);
  private final BundleContext extender;
  private final ServletContextManager servletContextManager;

  public SpringMvcConfigurationManagerImpl(BundleContext bndCtx,
      ServletContextManager servletContextManager) {
    this.extender = bndCtx;
    this.servletContextManager = servletContextManager;
  }

  @Override
  public void destroySpringMvcConfig(Bundle bnd) {
    if (mvcEnabled(bnd)) {
      DispatcherServlet dispatcherServlet = getDispacher(bnd);
      if (dispatcherServlet != null) {
        ConfigurableApplicationContext appCtx =
            (ConfigurableApplicationContext) dispatcherServlet.getWebApplicationContext();
        if (appCtx != null) {
          ServiceRegistration<Servlet> registration;
          try {
            registration = appCtx.getBean(ServiceRegistration.class);
            registration.unregister();
          } catch (Exception e) {
            log.error("ServiceRegistration was not saved?", e);
          }
          ConfigurableApplicationContext rootCtx =
              (ConfigurableApplicationContext) appCtx.getParent();
          if (rootCtx != null) {
            log.info("Shutting down spring context: {} ...", rootCtx.getDisplayName());
            rootCtx.close();
          }
          log.info("Shutting down spring context: {} ...", appCtx.getDisplayName());
          appCtx.close();
        }
      }
    }
  }

  @Override
  public void createSpringMvcConfig(Bundle bnd) {
    if (!mvcEnabled(bnd)) {
      log.debug("{} is not a spring mvc bundle.", bnd.getSymbolicName());
    } else if (bnd.getState() != Bundle.ACTIVE) {
      log.info("Bundle {} is not in active status.", bnd.getSymbolicName());
    } else {
      servletContextManager.scanBundleForServletContext(bnd);
      String ctxPath = getCtxPath(bnd);
      servletContextManager.submitServletContextTask(ctxPath, () -> {
        DispatcherServlet dispatcher = getDispacher(bnd);
        if (dispatcher == null) {
          GenericWebApplicationContext appCtx = getOrCreateSpringContext(bnd);
          ConfigurableApplicationContext rootCtx = getOrCreateSpringRootContext(bnd);
          if (rootCtx != null) {
            appCtx.setParent(rootCtx);
            rootCtx.refresh();
          }
          getOrCreateDispacher(bnd, appCtx);
        }
      });
    }
  }

  private static String getCtxPath(Bundle bnd) {
    return normalizeCtxPath(bnd.getHeaders().get(SpringMvcConstants.CONTEXT_ROOT));
  }

  @Override
  public void destroySpringMvcConfig(long bndId) {
    destroySpringMvcConfig(extender.getBundle(bndId));
  }

  @Override
  public void createSpringMvcConfig(long bndId) {
    createSpringMvcConfig(extender.getBundle(bndId));
  }

  private String getSpringContextName(Bundle bnd) {
    return SpringMvcConstants.CONTEXT_NAME_PREFIX + bnd.getSymbolicName();
  }

  private String getSpringRootContextName(Bundle bnd) {
    return SpringMvcConstants.ROOT_CONTEXT_NAME_PREFIX + bnd.getSymbolicName();
  }

  private String getDispatcherName(Bundle bnd) {
    return SpringMvcConstants.DISPATCHER_NAME_PREFIX + bnd.getSymbolicName();
  }

  private boolean mvcEnabled(Bundle bnd) {
    return Boolean.parseBoolean(bnd.getHeaders().get(SpringMvcConstants.ENABLED));
  }

  private GenericWebApplicationContext getOrCreateSpringContext(Bundle bnd) {
    String springContextName = getSpringContextName(bnd);
    DispatcherServlet dispatcher = getDispacher(bnd);
    GenericWebApplicationContext appCtx = null;
    if (dispatcher != null) {
      appCtx = (GenericWebApplicationContext) dispatcher.getWebApplicationContext();
    }
    if (appCtx == null) {
      log.info("Creating Spring Context: {} ......", springContextName);
      OsgiBundleResourcePatternResolver resLoader = new OsgiBundleResourcePatternResolver(bnd);
      appCtx = new GenericWebApplicationContext(ensureToGetServletContext(bnd));
      appCtx.setDisplayName(springContextName);
      appCtx.setResourceLoader(resLoader);
      ClassLoader loader = getBundleClassLoader(bnd);
      appCtx.setClassLoader(loader);
      appCtx.getBeanFactory()
          .registerSingleton(SpringMvcConstants.BUNDLE_CONTEXT, bnd.getBundleContext());
      // Workarround for pax web bug about classloader
      appCtx.getBeanFactory()
          .registerSingleton("setClassLoadInterceptor", new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
              registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler) {
                  Thread.currentThread().setContextClassLoader(getBundleClassLoader(bnd));
                  return true;
                }
              });
            }
          });
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
      appCtx.refresh();
    } else {
      log.info("Spring Context {} is already running.", getSpringContextName(bnd));
    }
    return appCtx;
  }

  private ConfigurableApplicationContext getOrCreateSpringRootContext(Bundle bnd) {
    String rootContextName = getSpringRootContextName(bnd);
    String configCls = bnd.getHeaders().get(SpringMvcConstants.ROOT_CONTEXT_CONFIG_CLASSES);
    String xmlCfgs = bnd.getHeaders().get(SpringMvcConstants.ROOT_CONTEXT_XML_LOCATIONS);
    if (configCls == null && xmlCfgs == null) {
      return null;
    }
    DispatcherServlet dispatcherServlet = getDispacher(bnd);
    WebApplicationContext webContext = null;
    if (dispatcherServlet != null) {
      webContext = dispatcherServlet.getWebApplicationContext();
    }
    GenericApplicationContext rootCtx = null;
    if (webContext != null) {
      rootCtx = (GenericApplicationContext) webContext.getParent();
    }
    if (rootCtx == null) {
      log.info("Creating Spring Root Context: {} ......", rootContextName);
      OsgiBundleResourcePatternResolver resLoader = new OsgiBundleResourcePatternResolver(bnd);
      rootCtx = new GenericApplicationContext();
      rootCtx.setDisplayName(rootContextName);
      rootCtx.setResourceLoader(resLoader);
      ClassLoader loader = getBundleClassLoader(bnd);
      rootCtx.setClassLoader(loader);
      rootCtx.getBeanFactory()
          .registerSingleton(SpringMvcConstants.BUNDLE_CONTEXT, bnd.getBundleContext());
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

  private static ClassLoader getBundleClassLoader(Bundle bnd) {
    return bnd.adapt(BundleWiring.class).getClassLoader();
  }

  private DispatcherServlet getDispacher(Bundle bnd) {
    BundleContext bndCtx = bnd.getBundleContext();
    DispatcherServlet dispatcherServlet = null;
    String servletName = getDispatcherName(bnd);
    Collection<ServiceReference<Servlet>> serviceReferences = Collections.EMPTY_LIST;
    try {
      serviceReferences = bndCtx.getServiceReferences(Servlet.class,
          String.format("(%s=%s)", HTTP_WHITEBOARD_SERVLET_NAME, servletName));
    } catch (InvalidSyntaxException e) {
      log.error("Unexpected", e);
    }
    if (serviceReferences.size() > 0) {
      ServiceReference<Servlet> ref = serviceReferences.iterator().next();
      dispatcherServlet = (DispatcherServlet) bndCtx.getService(ref);
      bndCtx.ungetService(ref);
    }
    return dispatcherServlet;
  }

  private void getOrCreateDispacher(Bundle bnd, GenericWebApplicationContext appCtx) {
    ServletContext servletContext = ensureToGetServletContext(bnd);
    BundleContext bndCtx = bnd.getBundleContext();
    DispatcherServlet dispatcherServlet = getDispacher(bnd);
    if (dispatcherServlet == null) {
      String servletPattern = bnd.getHeaders().get(SpringMvcConstants.SERVLET_PATTERN);
      if (servletPattern == null) {
        servletPattern = "/*";
      } else if (!servletPattern.endsWith("*")) {
        if (!servletPattern.endsWith("/")) {
          servletPattern += "/";
        }
        servletPattern += "*";
      }
      dispatcherServlet = new DispatcherServlet();
      dispatcherServlet.setApplicationContext(appCtx);
      Dictionary<String, String> props = new Hashtable<>();
      props.put(HTTP_WHITEBOARD_SERVLET_PATTERN, servletPattern);
      props.put(HTTP_WHITEBOARD_SERVLET_NAME, getDispatcherName(bnd));
      props.put(HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED, "true");
      props.put(HTTP_WHITEBOARD_CONTEXT_SELECT,
          String.format("(osgi.http.whiteboard.context.name=%s)",
              servletContext.getServletContextName()));
      props.put(SpringMvcConstants.EXTENDER_NAME, "true");
      log.info("Registering Servlet: {} ...", getDispatcherName(bnd));
      ServiceRegistration<Servlet> registration =
          bndCtx.registerService(Servlet.class, dispatcherServlet, props);
      appCtx.getBeanFactory().registerSingleton("dispatcherServletRegistration", registration);
    }
  }

  private Set<DispatcherServlet> listDispatchers() {
    Set<DispatcherServlet> dispatchers = new HashSet<>();
    Collection<ServiceReference<Servlet>> serviceReferences;
    try {
      serviceReferences = extender.getServiceReferences(Servlet.class,
          String.format("(%s=%s)", SpringMvcConstants.EXTENDER_NAME, "true"));
      serviceReferences.forEach((ref) -> {
        Servlet servlet = extender.getService(ref);
        if (servlet instanceof DispatcherServlet) {
          dispatchers.add((DispatcherServlet) servlet);
        }
        extender.ungetService(ref);
      });
    } catch (InvalidSyntaxException e) {
      log.error("Unexpected Exception", e);
    }
    return dispatchers;
  }

  /**
   * ServletContext may not be available,sometimes we did need to wait.
   *
   * @param bnd The Bundle that will use the servlet context
   * @return ServletContext
   */
  private ServletContext ensureToGetServletContext(Bundle bnd) {
    ServletContext servletContext = null;
    int tryCount = 0;
    while (tryCount < 10 && servletContext == null) {
      servletContext =
        servletContextManager.getServletContext(bnd.getBundleContext(), getCtxPath(bnd));
      if (tryCount > 0) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      tryCount++;
    }
    return servletContext;
  }

  @Override
  public void scanAndLoadSpringMvcConfigs() {
    Arrays.stream(extender.getBundles())
        .filter((b) -> b.getState() == Bundle.ACTIVE && mvcEnabled(b))
        .forEach(this::createSpringMvcConfig);
  }

  @Override
  public void destroyAllSpringMvcConfigs() {
    Arrays.stream(extender.getBundles())
        .filter((b) -> b.getState() == Bundle.ACTIVE && mvcEnabled(b))
        .forEach(this::destroySpringMvcConfig);
  }

  @Override
  public Collection<ConfigurableApplicationContext> listSpringContexts() {
    Set<ConfigurableApplicationContext> applicationContexts = new HashSet<>();
    listDispatchers().forEach((dispatcher) -> {
      ConfigurableApplicationContext applicationContext =
          (ConfigurableApplicationContext) dispatcher.getWebApplicationContext();
      if (applicationContext != null) {
        if (applicationContext.getParent() != null) {
          applicationContexts.add((ConfigurableApplicationContext) applicationContext.getParent());
        }
        applicationContexts.add(applicationContext);
      }
    });
    return applicationContexts;
  }

  @Override
  public Collection<ServletContext> listServletContexts() {
    return listDispatchers().stream()
        .filter((s) -> s.getWebApplicationContext() != null)
        .map((s) -> s.getWebApplicationContext().getServletContext())
        .collect(Collectors.toSet());
  }
}
