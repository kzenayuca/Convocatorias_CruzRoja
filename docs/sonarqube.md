# SonarQube

```bash
docker compose --profile quality up -d
cd backend && ./mvnw verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=TOKEN
```

En Linux configure `sudo sysctl -w vm.max_map_count=524288` si Elasticsearch falla. Para servidor persistente use `SONAR_HOST_URL` y `SONAR_TOKEN`. Corrija los hallazgos o ajuste código/pruebas si falla el quality gate.
