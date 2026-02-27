plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.flywaydb.flyway") version "10.20.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

flyway {
    url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/incident_explainer"
    user = System.getenv("DB_USER") ?: "incident_explainer"
    password = System.getenv("DB_PASSWORD") ?: "incident_explainer"
    locations = arrayOf("filesystem:${projectDir}/src/main/resources/db/migration")
}
