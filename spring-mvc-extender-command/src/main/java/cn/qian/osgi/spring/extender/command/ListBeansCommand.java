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
package cn.qian.osgi.spring.extender.command;

import cn.qian.osgi.spring.extender.api.SpringMvcConfigurationManager;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;

@Service
@Command(scope = "spring", name = "list-beans", description = "List Beans in Spring Mvc Contexts")
public class ListBeansCommand implements Action {
  @Argument(name = "bundleId", description = "Bundle ID")
  long bundleId;
  @Reference
  private SpringMvcConfigurationManager springMvcConfigurationManager;

  @Override
  public Object execute() {
    ShellTable table = new ShellTable();
    table.column("Context");
    table.column("BeanName");
    table.column("BeanType");
    if (bundleId != 0) {
      springMvcConfigurationManager.getSpringRootContextBeans(bundleId)
        .forEach((n, b) -> table.addRow().addContent("rootContext", n, b.getClass().getName()));
      springMvcConfigurationManager.getSpringContextBeans(bundleId)
        .forEach((n, b) -> table.addRow().addContent("dispatcherContext", n, b.getClass().getName()));
    }
    table.print(System.out);
    return null;
  }
}
