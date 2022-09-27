You can build and drop it to the apache-karaf/deploy directory:

```bash
mvn clean package
karaf@root()> feature:repo-add mvn:cn.qian.osgi/demo-thymeleaf-features/LATEST/xml/features
karaf@root()> feature:install demo-thymeleaf
# You will get "it worked"
curl http://localhost:8181/mvc2/
# You will get html result rendered by thymeleaf
curl http://localhost:8181/mvc2/hello/world
```
