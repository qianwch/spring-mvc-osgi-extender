package cn.qian.osgi.spring.extender.impl;

import cn.qian.osgi.spring.extender.api.SpringMvcConstants;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
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
import org.osgi.framework.ServiceRegistration;
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
  private ExecutorService executor = Executors.newCachedThreadPool();
  private final BlockingQueue<Runnable> servletContextCreatingTasks = new LinkedBlockingQueue<>();

  private final Map<String, ServletContext> servletContexts =
      Collections.synchronizedMap(new HashMap<>());
  private final Map<String, ServiceRegistration<ServletContextHelper>> servletContextRegs =
      Collections.synchronizedMap(new HashMap<>());
  private final Map<String, BlockingQueue<Runnable>> servletContextTaskQs =
      Collections.synchronizedMap(new HashMap<>());
  private Map<String, Thread> servletContextTaskMonitors =
      Collections.synchronizedMap(new HashMap<>());
  private Thread servletContextMonitor;

  public ServletContextManager(BundleContext extenderContext) {
    this.extenderContext = extenderContext;
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
      if (!servletContexts.containsKey(ctxPath)) {
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
          if (!servletContexts.containsKey(p)) {
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
      servletContexts.put(p, extenderContext.getService(ref));
      extenderContext.ungetService(ref);
    } else if (!"".equals(p) && !"/".equals(p)) {
      doCreatingServletContext(p);
      log.info("Trying to create ServletContext: {}", p);
    }
  }

  private void doCreatingServletContext(String p) {
    if ("/".equals(normalizeCtxPath(p))) {
      return;
    }
    Runnable task = () -> {
      Dictionary<String, String> props = new Hashtable<>();
      props.put(HTTP_WHITEBOARD_CONTEXT_NAME, contextPathToName(p));
      props.put(HTTP_WHITEBOARD_CONTEXT_PATH, p);
      props.put(SpringMvcConstants.EXTENDER_NAME, "true");
      ServiceRegistration<ServletContextHelper> registration =
          httpWhiteBoardCtx.registerService(ServletContextHelper.class, new ServletContextHelper() {
          }, props);
      servletContextRegs.put(p, registration);
    };
    servletContextCreatingTasks.add(task);
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
    if (servletContextMonitor != null) {
      servletContextMonitor.interrupt();
    }
    if (servletContextTaskMonitors != null) {
      servletContextTaskMonitors.values().forEach(Thread::interrupt);
      servletContextTaskMonitors.clear();
      servletContextTaskMonitors = null;
    }
  }

  @Override
  public ServletContext addingService(ServiceReference<ServletContext> reference) {
    BundleContext bndCtx = reference.getBundle().getBundleContext();
    ServletContext servletContext = bndCtx.getService(reference);
    String contextPath = normalizeCtxPath(servletContext.getContextPath());
    log.info("ServletContext {} is now starting up......", contextPath);
    if ("/".equals(contextPath)) {
      httpWhiteBoardCtx = bndCtx;
      servletContextMonitor = new Thread(() -> {
        boolean interrupted = false;
        while (!interrupted) {
          try {
            Runnable task = servletContextCreatingTasks.take();
            executor.execute(task);
          } catch (InterruptedException ex) {
            interrupted = true;
          }
        }
      });
      servletContextMonitor.setName(
          ServletContextManager.class.getPackage().getName() + ".ServletContextCreator");
      servletContextMonitor.start();
      scanBundlesForServletContexts();
    }
    servletContexts.put(contextPath, servletContext);
    initServletContextTaskQ(contextPath);
    if (servletContextTaskMonitors.get(contextPath) == null) {
      Thread contextTaskMonitor = new Thread(() -> {
        boolean interrupted = false;
        while (!interrupted) {
          try {
            Runnable task = servletContextTaskQs.get(contextPath).take();
            executor.execute(task);
          } catch (InterruptedException ex) {
            interrupted = true;
          }
        }
      });
      contextTaskMonitor.setName(
          ServletContextManager.class.getPackage().getName() + contextPath.replace('/', '_'));
      servletContextTaskMonitors.put(contextPath, contextTaskMonitor);
      contextTaskMonitor.start();
    }
    return servletContext;
  }

  private synchronized void initServletContextTaskQ(String contextPath) {
    if (servletContextTaskQs.get(contextPath) == null) {
      BlockingQueue<Runnable> servletContextTasks = new LinkedBlockingQueue<>();
      servletContextTaskQs.put(contextPath, servletContextTasks);
    }
  }

  public void submitServletContextTask(String path, Runnable task) {
    initServletContextTaskQ(path);
    try {
      servletContextTaskQs.get(path).put(task);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void modifiedService(ServiceReference<ServletContext> reference, ServletContext service) {

  }

  @Override
  public void removedService(ServiceReference<ServletContext> reference, ServletContext service) {
    BundleContext bndCtx = reference.getBundle().getBundleContext();
    ServletContext servletContext = bndCtx.getService(reference);
    String ctxPath = normalizeCtxPath(servletContext.getContextPath());
    log.info("ServletContext {} is now shutting down......", ctxPath);
    if ("/".equals(ctxPath)) {
      servletContextMonitor.interrupt();
      servletContextMonitor = null;
    }
    if (servletContextTaskMonitors != null && servletContextTaskMonitors.get(ctxPath) != null) {
      servletContextTaskMonitors.get(ctxPath).interrupt();
      servletContextTaskMonitors.remove(ctxPath);
    }
  }
}
