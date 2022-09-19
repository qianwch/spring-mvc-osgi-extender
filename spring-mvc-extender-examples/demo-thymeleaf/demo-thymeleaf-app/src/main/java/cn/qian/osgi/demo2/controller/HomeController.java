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
package cn.qian.osgi.demo2.controller;

import cn.qian.osgi.demo2.model.HelloMsg;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HomeController {
  @GetMapping("/")
  @ResponseBody
  public String home() {
    return "it worked!";
  }

  @GetMapping("/hello/{name}")
  public ModelAndView hello(@PathVariable("name") String name) {
    HelloMsg msg = new HelloMsg();
    msg.setName(name);
    msg.setMsg(String.format("hello %s", name));
    ModelAndView modelAndView = new ModelAndView("msg");
    modelAndView.addObject("msg", msg);
    return modelAndView;
  }
}
