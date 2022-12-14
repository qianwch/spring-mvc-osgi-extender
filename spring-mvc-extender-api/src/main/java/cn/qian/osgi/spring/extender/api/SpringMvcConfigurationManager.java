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

import java.util.Collection;
import java.util.Map;
import javax.servlet.ServletContext;
import org.osgi.framework.Bundle;
import org.springframework.context.ConfigurableApplicationContext;

public interface SpringMvcConfigurationManager {
  void destroySpringMvcConfig(Bundle bnd);

  void createSpringMvcConfig(Bundle bnd);

  void destroySpringMvcConfig(long bndId);

  void createSpringMvcConfig(long bndId);

  Map<String, Object> getSpringContextBeans(Bundle bnd);

  Map<String, Object> getSpringContextBeans(long bndId);

  Map<String, Object> getSpringRootContextBeans(Bundle bnd);

  Map<String, Object> getSpringRootContextBeans(long bndId);

  void scanAndLoadSpringMvcConfigs();

  void destroyAllSpringMvcConfigs();

  Collection<ConfigurableApplicationContext> listSpringContexts();

  Collection<ServletContext> listServletContexts();
}
