This is the Spring MVC + Thymeleaf + Mybatis demonstrating project.

```bash
mvn clean package
karaf@root()> feature:repo-add mvn:cn.qian.osgi/demo-mybatis-features/LATEST/xml/features
karaf@root()> feature:install demo-mybatis
# You will get "it worked"
curl http://localhost:8181/mvc3/
# You will get json result {"name":"world","msg":"Hello world","id":1}, and just for demo, a line will be inserted by 
curl http://localhost:8181/mvc3/hello/world
# You will get the html page rendered by thymeleaf contains the hello world.
curl http://localhost:8181/mvc3/hello/
# The upper message will be deleted from db
curl -XDELETE -L http://localhost:8181/mvc3/hello/1

```
