package cn.qian.osgi.demo.controller;

import cn.qian.osgi.demo.model.HelloMsg;
import cn.qian.osgi.demo.model.HelloMsgMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class HelloController implements InitializingBean {
  @Autowired
  private HelloMsgMapper msgMapper;

  @Autowired
  private DataSource ds;

  @GetMapping("/hello/{name}")
  public HelloMsg hello(@PathVariable("name") String name) {
    HelloMsg msg = new HelloMsg();
    msg.setName(name);
    msg.setMsg("Hello "+name);
    msgMapper.createMsg(msg);
    return msg;
  }

  @DeleteMapping("/hello/{id}")
  public ModelAndView delete(@PathVariable("id") int id) {
    HelloMsg msg = new HelloMsg();
    msg.setId(id);
    msgMapper.deleteMsg(msg);
    return new ModelAndView("redirect:/hello");
  }


  @GetMapping("/hello")
  public ModelAndView listMsg() {
    ModelAndView mv = new ModelAndView("hello");
    mv.addObject("msgs", msgMapper.listMsg());
    return mv;
  }

  @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
  public void init() {
    try (Connection conn = ds.getConnection()) {
      ResultSet rs = conn.getMetaData().getTables(null, null, "HELLO_MSG", new String[] {"TABLE"});
      if (!rs.next()) {
        Statement stmt = conn.createStatement();
        stmt.execute("create table hello_msg (id IDENTITY primary key, name varchar(64), msg varchar(128))");
        stmt.close();
      }
      rs.close();
    } catch (Exception ex) {
    }
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    init();
  }
}
