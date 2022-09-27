package cn.qian.osgi.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DefaultController {

  @GetMapping("/")
  public String home() {
    return "it worked!";
  }

}
