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
package cn.qian.osgi.spring.extender.api;

public class SpringMvcConstants {
  public static final String ENABLED = "Spring-Mvc-Enabled";
  public static final String SERVLET_PATTERN = "Spring-Mvc-UrlPattern";
  public static final String CONTEXT_ROOT = "Spring-Mvc-ContextPath";
  public static final String ROOT_CONTEXT_CONFIG_CLASSES = "Spring-Root-Context-Config-Classes";
  public static final String CONTEXT_CONFIG_CLASSES = "Spring-Context-Config-Classes";
  public static final String ROOT_CONTEXT_XML_LOCATIONS = "Spring-Root-Context-Xml-Locations";
  public static final String CONTEXT_XML_LOCATIONS = "Spring-Context-Xml-Locations";
  public static final String EXTENDER_NAME = "cn.qian.osgi.spring.extender";
  public static final String CONTEXT_NAME_PREFIX =
      SpringMvcConstants.EXTENDER_NAME + ".spring.context.";
  public static final String ROOT_CONTEXT_NAME_PREFIX =
    SpringMvcConstants.EXTENDER_NAME + ".spring.root-context.";
  public static final String DISPATCHER_NAME_PREFIX =
    SpringMvcConstants.EXTENDER_NAME + ".dispatcher.servlet.";
  public static final String BUNDLE_CONTEXT = "bundleContext";
}
