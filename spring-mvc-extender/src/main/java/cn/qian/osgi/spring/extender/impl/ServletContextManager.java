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

import cn.qian.osgi.spring.extender.api.SpringMvcConstants;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;

@SuppressWarnings({"unchecked", "SpellCheckingInspection"})
public class ServletContextManager
  implements ServiceTrackerCustomizer<ServletContext, ServletContext> {
  private volatile BundleContext httpWhiteBoardCtx;
  private final BundleContext extenderContext;
  private final Logger log = LoggerFactory.getLogger(ServletContextManager.class);
  private ExecutorService executor = Executors.newSingleThreadExecutor();
  /**
   * Task queue for ServletContexts that are available, except the servlet-context-creating tasks.
   * The tasks are due to run now.
   */
  private final BlockingQueue<SimpleEntry<String, Runnable>> jobs = new LinkedBlockingQueue<>();
  /**
   * Available servlet contexts.
   */
  private final Set<String> liveCtxPath = Collections.synchronizedSet(new HashSet<>());
  private final Set<String> pendingCtxPath = Collections.synchronizedSet(new HashSet<>());
  /**
   * The tasks will not run until the serlvet contexts(key is context path) are available.
   */
  private final Map<String, List<Runnable>> servletContextTaskQs =
    Collections.synchronizedMap(new HashMap<>());
  private Thread jobWaitingThread;

  public ServletContextManager(BundleContext extenderContext) {
    this.extenderContext = extenderContext;
    setupJobsWaitingThread();
  }

  private void setupJobsWaitingThread() {
    jobWaitingThread = new Thread(() -> {
      boolean interrupted = false;
      while (!interrupted) {
        try {
          executor.execute(jobs.take().getValue());
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    }, ServletContextManager.class.getPackage().getName() + "-jobs");
    jobWaitingThread.start();
    //Scheule bundle scannning task
    submitServletContextTask("/", this::scanBundlesForServletContexts);
  }

  public static String normalizeCtxPath(String ctxPath) {
    if (ctxPath == null || "".equals(ctxPath)) {
      ctxPath = "/";
    } else if (ctxPath.endsWith("/")) {
      ctxPath = ctxPath.substring(0, ctxPath.length() - 1);
    }
    if (!ctxPath.startsWith("/")) {
      ctxPath = "/" + ctxPath;
    }
    return ctxPath;
  }

  public void scanBundleForServletContext(Bundle bnd) {
    if ("true".equals(bnd.getHeaders().get(SpringMvcConstants.ENABLED))) {
      String ctxPath = normalizeCtxPath(bnd.getHeaders().get(SpringMvcConstants.CONTEXT_ROOT));
      if (!liveCtxPath.contains(ctxPath)) {
        createServletContext(ctxPath);
      }
    }
  }

  public void scanBundlesForServletContexts() {
    Arrays.stream(extenderContext.getBundles())
      .filter(bnd -> "true".equals(bnd.getHeaders().get(SpringMvcConstants.ENABLED)))
      .map(bnd -> normalizeCtxPath(bnd.getHeaders().get(SpringMvcConstants.CONTEXT_ROOT)))
      .collect(Collectors.toSet())
      .forEach(p -> {
        if (!liveCtxPath.contains(p)) {
          createServletContext(p);
        }
      });
  }

  private void createServletContext(String p) {
    Collection<ServiceReference<ServletContext>> serviceReferences = Collections.EMPTY_LIST;
    try {
      serviceReferences = extenderContext.getServiceReferences(ServletContext.class,
        String.format("(osgi.web.contextpath=%s)", p));
    } catch (InvalidSyntaxException e) {
      log.error("Unexpected", e);
    }
    if (serviceReferences.size() > 0) {
      ServiceReference<ServletContext> ref = serviceReferences.iterator().next();
      extenderContext.ungetService(ref);
    } else if (!"".equals(p) && !"/".equals(p)) {
      doCreatingServletContext(p);
    }
  }

  private void doCreatingServletContext(String p) {
    if ("/".equals(normalizeCtxPath(p)) || pendingCtxPath.contains(p)) {
      return;
    }
    Runnable task = () -> {
      log.info("Trying to create ServletContext: {}", p);
      Dictionary<String, String> props = new Hashtable<>();
      props.put(HTTP_WHITEBOARD_CONTEXT_NAME, contextPathToName(p));
      props.put(HTTP_WHITEBOARD_CONTEXT_PATH, p);
      props.put(SpringMvcConstants.EXTENDER_NAME, "true");
      httpWhiteBoardCtx.registerService(ServletContextHelper.class, new ServletContextHelper() {
      }, props);
    };
    pendingCtxPath.add(p);
    // To create servlet contexts, we need to wait default context to be available.
    submitServletContextTask("/", task);
  }

  public static String contextPathToName(String p) {
    return "http" + p.replace('/', '_');
  }

  public ServletContext getServletContext(BundleContext bndCtx, String ctxPath) {
    Collection<ServiceReference<ServletContext>> serviceReferences = Collections.EMPTY_LIST;
    try {
      serviceReferences = bndCtx.getServiceReferences(ServletContext.class,
        String.format("(osgi.web.contextpath=%s)", ctxPath));
    } catch (InvalidSyntaxException e) {
      log.error("Unexpected", e);
    }
    ServletContext servletContext = null;
    if (serviceReferences.size() > 0) {
      ServiceReference<ServletContext> ref = serviceReferences.iterator().next();
      servletContext = bndCtx.getService(ref);
      bndCtx.ungetService(ref);
    }
    return servletContext;
  }

  public void shutdown() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
    if (jobWaitingThread != null) {
      jobWaitingThread.interrupt();
      jobWaitingThread = null;
    }
  }

  private synchronized void initServletContextTaskQ(String contextPath) {
    if (servletContextTaskQs.get(contextPath) == null) {
      List<Runnable> servletContextTasks = new LinkedList<>();
      servletContextTaskQs.put(contextPath, servletContextTasks);
    }
  }

  /**
   * submit a task which will run when the servlet context path is available
   */
  public synchronized void submitServletContextTask(String path, Runnable task) {
    if (liveCtxPath.contains(path)) {
      jobs.add(new SimpleEntry<>(path, task));
    } else {
      initServletContextTaskQ(path);
      servletContextTaskQs.get(path).add(task);
    }
  }

  @Override
  public synchronized ServletContext addingService(ServiceReference<ServletContext> reference) {
    BundleContext bndCtx = reference.getBundle().getBundleContext();
    ServletContext servletContext = bndCtx.getService(reference);
    String contextPath = normalizeCtxPath(servletContext.getContextPath());
    log.info("ServletContext {} is now starting up......", contextPath);
    liveCtxPath.add(contextPath);
    pendingCtxPath.remove(contextPath);
    if ("/".equals(contextPath)) {
      httpWhiteBoardCtx = bndCtx;
    }
    //Move the tasks from waiting queue to running queue
    if (servletContextTaskQs.get(contextPath) != null) {
      jobs.addAll(servletContextTaskQs.get(contextPath)
        .stream()
        .map((r) -> new SimpleEntry<>(contextPath, r))
        .collect(Collectors.toList()));
      servletContextTaskQs.get(contextPath).clear();
      servletContextTaskQs.remove(contextPath);
    }
    return servletContext;
  }

  @Override
  public void modifiedService(ServiceReference<ServletContext> reference, ServletContext service) {

  }

  @Override
  public synchronized void removedService(ServiceReference<ServletContext> reference,
    ServletContext service) {
    BundleContext bndCtx = reference.getBundle().getBundleContext();
    ServletContext servletContext = bndCtx.getService(reference);
    String ctxPath = normalizeCtxPath(servletContext.getContextPath());
    liveCtxPath.remove(ctxPath);
    log.info("ServletContext {} is now shutting down......", ctxPath);
  }
}
