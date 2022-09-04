You can build and drop it to the apache-karaf/deploy directory:

```bash
mvn clean package
cp target/*.jar ~/apache-karaf-4.4.1/deploy
# You will get "it worked"
curl http://localhost:8181/mvc1/
# You will get json result {"name":"world","msg":"hello world"}
curl http://localhost:8181/mvc1/hello/world
```
