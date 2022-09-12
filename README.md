# What is it, and what can it do?

It is an osgi-extender-bundle which is triggered by the bundle events.  
Bundles which contains spring mvc configurations could be plugged into osgi **http whiteboard**, the
extender will:

* Use the default or create a new ServletContext.
* Create spring contexts specified by the bundle headers.
* Create Dispatcher Servlet instance and associate spring context with it.
* Support creating a parent spring context for the dispatcher spring context.

# Motivation

In microservice world, we use spring boot as the application scaffold. It is good, but the overall
memory footprint is really a overhead for many companies.
And in some circumstances monolithic application is just fine.  
Yes, we can package spring boot application in WAR, and deploy all spring boot WARs in web
containers. But memory usage could be also high as the duplicated spring jars will occupy jvm
metaspace.  
Using spring in osgi is the right answer. Currently, there is no spring boot libraries as osgi
bundles. We can only wrap spring boot jars as bundles. But I did not successfully make to run spring
boot application that uses shared (not packed in) spring boot libraries.  
But I made to run spring mvc applications in apache karaf using shared spring library bundles
released by servicemix.

# Why not Spring-DM or Eclipse Gemini?

Spring-DM has been donated to Eclipse, now it
is [Eclipse Gemini](https://www.eclipse.org/gemini/blueprint/documentation/reference/3.0.0.M01/html-single/index.html)
.  
Both of them only support xml-config. Nowadays annotation config is preferred. And it is not a simple work to get spring mvc running up.

# Prerequisite

* Apache Karaf 4.x: the only tested container.
* Spring bundles released by ServiceMix: you can install them automatically when installing
  spring-mvc-extender-features.
* Gemini Blueprint IO library: it is vital for the solution. It is declared in the feature xml.

Everything needed would be installed with the feature.

# How to Install?

```bash
# Clone the source code
git clone https://github.com/qianwch/spring-mvc-extender.git
# Build
cd spring-mvc-extender
mvn clean package install
# Install the features to karaf
feature:repo-add mvn:cn.qian.osgi/spring-mvc-extender-features/LATEST/xml/features
feature:install spring-mvc-extender
# Optional
feature:install spring-mvc-extender-command
```

# How to use?

The extender will use some Bundle Headers to get spring mvc configured:

| Header                             | Default Value | Required | Description                                                                                                                                                                                                                                                             |
|------------------------------------|---------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spring-Mvc-Enabled                 | -             | Yes      | Must set to true                                                                                                                                                                                                                                                        | 
| Spring-Mvc-ContextPath             | /             | No       | The ServletContext Path for spring mvc app. If not set, the default context(/) is used. If the servlet context does not exist yet, it will be created. ServletContext path may be shared by more that one bundles. IMO, Using the default servlet context is preferred. |
| Spring-Mvc-UrlPattern              | /*            | No       | The url-pattern for dispatcher servlet. You would like to set unique pattern for each bundles within the same servlet context. The extender does not validate the uniqueness of the url patterns. So you need to ensure it.                                             |
| Spring-Root-Context-Config-Classes | -             | No       | If you are using hierarchical spring context, you could specify the configuration full class names (separated by comma) for root spring context.                                                                                                                        |
| Spring-Context-Config-Classes      | -             | No       | Spring configuration full class names (separated by comma)                                                                                                                                                                                                              |
| Spring-Root-Context-Xml-Locations  | -             | No       | Xml configurations for root spring context. It is supported but not preferred.                                                                                                                                                                                          |
| Spring-Context-Xml-Locations       | -             | No       | Xml configurations for spring context. It is supported but not preferred.                                                                                                                                                                                               |

If Spring-Mvc-Enabled is set to true and no spring configuration is set, the extender will try to
load /META-INF/spring/*.xml.  
Please note that, this extender does NOT support WAB/WAR bundle, the **Web-ContextPath** header will not be used by the extender. The reason is simple, we could not dynamically register servlets to ServletContext for WAR/WAB from the extender.  
You could add the bundler headers to maven-bundle-plugin config, like this:

```xml

<plugin>
  <groupId>org.apache.felix</groupId>
  <artifactId>maven-bundle-plugin</artifactId>
  <version>4.2.1</version>
  <extensions>true</extensions>
  <configuration>
    <instructions>
      <Spring-Mvc-Enabled>true</Spring-Mvc-Enabled>
      <Spring-Mvc-UrlPattern>/mvc1/*</Spring-Mvc-UrlPattern>
      <Spring-Context-Config-Classes>cn.qian.osgi.demo1.config.MvcConfiguration</Spring-Context-Config-Classes>
      <Export-Package>!*</Export-Package>
      <Import-Package>
        org.springframework.beans.factory.config,
        org.springframework.stereotype,
        org.springframework.context,
        org.springframework.beans,
        org.springframework.beans.factory,
        org.springframework.context.annotation,
        org.springframework.web.context.support,
        org.springframework.web.context,
        org.springframework.web.bind.annotation,
        org.springframework.web.servlet,
        org.springframework.web.servlet.view,
        org.springframework.http.converter,
        org.springframework.http.converter.json,
        org.springframework.cglib.core,
        org.springframework.cglib.proxy,
        org.springframework.cglib.reflect,
        *
      </Import-Package>
      <_noee>true</_noee>
    </instructions>
    <buildDirectory>target</buildDirectory>
  </configuration>
  <executions>
    <execution>
      <id>bundle-manifest</id>
      <phase>package</phase>
      <goals>
        <goal>bundle</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Spring framework heavily uses dynamic class loading. As you have seen, spring packages need to be
specified manually.  
Please check the example project. I will add more examples later.  
# Karaf Shell Commands
Furthermore, the extender adds some karaf shell commands:
* spring:scan
  * Scan all bundles for spring mvc configs
* spring:stop [-a] [bundleId]
  * Stop spring mvc contexts. If "-a", all contexts will be shutdown. 
* spring:list
  * List running spring mvc contexts
