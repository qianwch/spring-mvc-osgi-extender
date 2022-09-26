package cn.qian.osgi.demo.model;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

@Component
public interface HelloMsgMapper {
  @Select("SELECT * from hello_msg")
  List<HelloMsg> listMsg();

  @Insert(value = "insert into hello_msg(name,msg) values (#{name}, #{msg})")
  @Options(useGeneratedKeys = true, keyProperty="id")
  void createMsg(HelloMsg msg);

  @Delete("delete from hello_msg where id=#{id}")
  void deleteMsg(HelloMsg msg);
}
