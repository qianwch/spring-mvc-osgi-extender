You can build and drop it to the apache-karaf/deploy directory:

```bash
mvn clean package
feature:repo-add mvn:cn.qian.osgi/demo-mybatis-features/LATEST/xml/features
# You will get "it worked"
curl http://localhost:8181/mvc1/
# You will get json result {"name":"world","msg":"hello world"}
curl http://localhost:8181/mvc1/hello/world
```
